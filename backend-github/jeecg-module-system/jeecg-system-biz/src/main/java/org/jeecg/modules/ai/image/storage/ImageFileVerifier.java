package org.jeecg.modules.ai.image.storage;

import java.io.IOException;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.util.zip.CRC32;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;

/** Checks dimensions before decoding; encoded bytes are read from disk, not accumulated in RAM. */
public final class ImageFileVerifier {
    private final int maxDimension;
    public ImageFileVerifier(int maxDimension) { this.maxDimension=maxDimension; }

    private void verifyPng(Path path) throws IOException {
        try (DataInputStream input=new DataInputStream(Files.newInputStream(path))) {
            input.readLong();
            byte[] buffer=new byte[65536]; boolean ended=false;
            while (!ended) {
                int length=input.readInt();
                if (length<0 || length>Files.size(path)-12) throw new IllegalArgumentException("Invalid PNG chunk");
                byte[] type=new byte[4]; input.readFully(type); CRC32 crc=new CRC32(); crc.update(type);
                int remaining=length;
                while (remaining>0) {
                    int count=Math.min(buffer.length,remaining); input.readFully(buffer,0,count); crc.update(buffer,0,count); remaining-=count;
                }
                if ((input.readInt() & 0xffffffffL)!=crc.getValue()) throw new IllegalArgumentException("PNG checksum mismatch");
                ended=type[0]=='I' && type[1]=='E' && type[2]=='N' && type[3]=='D';
                if (ended && (length!=0 || input.read()!=-1)) throw new IllegalArgumentException("Invalid PNG end");
            }
        } catch (java.io.EOFException e) { throw new IllegalArgumentException("Incomplete PNG"); }
    }

    public void verify(Path path, String mediaType) throws IOException {
        try (FileImageInputStream stream=new FileImageInputStream(path.toFile())) {
            byte[] signature=new byte[8];
            if (stream.read(signature) != 8) throw new IllegalArgumentException("Invalid image");
            boolean png=signature[0]==(byte)137 && signature[1]==80 && signature[2]==78 && signature[3]==71
                    && signature[4]==13 && signature[5]==10 && signature[6]==26 && signature[7]==10;
            boolean jpeg=signature[0]==(byte)255 && signature[1]==(byte)216 && signature[2]==(byte)255;
            if (!(png && "image/png".equals(mediaType)) && !(jpeg && "image/jpeg".equals(mediaType)))
                throw new IllegalArgumentException("Media signature does not match");
            if (png) verifyPng(path);
            else {
                stream.seek(stream.length()-2);
                if (stream.readUnsignedShort()!=0xffd9) throw new IllegalArgumentException("Incomplete JPEG");
            }
            stream.seek(0);
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported image");
            ImageReader reader=readers.next();
            try {
                reader.setInput(stream,true,true);
                int width=reader.getWidth(0), height=reader.getHeight(0);
                if (width < 1 || height < 1 || width > maxDimension || height > maxDimension)
                    throw new IllegalArgumentException("Image dimensions exceed decoding budget");
                if (reader.read(0) == null) throw new IllegalArgumentException("Invalid image");
            } catch (IOException e) { throw new IllegalArgumentException("Incomplete image"); }
            finally { reader.dispose(); }
        }
    }
}
