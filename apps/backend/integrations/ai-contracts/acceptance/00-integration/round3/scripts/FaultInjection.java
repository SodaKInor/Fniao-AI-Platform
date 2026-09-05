package org.jeecg.modules.ai.acceptance;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.jeecg.modules.ai.asset.domain.*;
import org.jeecg.modules.ai.capability.domain.*;
import org.jeecg.modules.ai.image.domain.*;
import org.jeecg.modules.ai.job.domain.*;
import org.jeecg.modules.ai.provider.domain.*;
import org.jeecg.modules.ai.result.domain.*;
import org.jeecg.modules.ai.stream.domain.*;
import org.jeecg.modules.ai.video.domain.*;
import org.jeecg.modules.ai.asset.port.*;
import org.jeecg.modules.ai.capability.port.*;
import org.jeecg.modules.ai.image.port.*;
import org.jeecg.modules.ai.job.port.*;
import org.jeecg.modules.ai.result.port.*;
import org.jeecg.modules.ai.stream.port.*;
import org.jeecg.modules.ai.video.port.*;

/** Acceptance-only classpath overlay. Never compiled into the application artifact. */
@Component
public class FaultInjection implements BeanPostProcessor {
    private static final Path ROOT=Paths.get("/acceptance/control");
    private String mode() {
        try { return new String(Files.readAllBytes(ROOT.resolve("mode")),StandardCharsets.UTF_8).trim(); }
        catch (IOException e) { return "normal"; }
    }
    private synchronized void record(String type,String id) {
        try { Files.write(ROOT.resolve("events.tsv"),(type+"\t"+id+"\t"+mode()+"\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch (IOException e) { throw new IllegalStateException("Acceptance audit unavailable"); }
    }
    @Override public Object postProcessAfterInitialization(Object bean,String name) {
        if (name.equals("inferenceProvider")) {
            InferenceProvider real=(InferenceProvider)bean;
            return (InferenceProvider) request -> {
                String scenario=mode(); record("dispatch",request.getRequestId());
                if (scenario.equals("delay")) {
                    try { Thread.sleep(4500); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                if (scenario.equals("auth")) throw new ProviderException(ErrorCode.PROVIDER_AUTH,ExecutionCertainty.NOT_STARTED,"Acceptance provider credential rejection");
                if (scenario.equals("unknown")) throw new ProviderException(ErrorCode.RESULT_UNKNOWN,ExecutionCertainty.UNKNOWN,"Acceptance lost provider response");
                return real.infer(request);
            };
        }
        if (name.equals("providerArtifactReader")) {
            ProviderArtifactReader real=(ProviderArtifactReader)bean;
            return (ProviderArtifactReader) (snapshot,artifact,limit) -> {
                record("collect",artifact.getMetadata().getFileName());
                InputStream input=real.open(snapshot,artifact,limit);
                if (!mode().equals("truncate")) return input;
                return new FilterInputStream(input) {
                    private int remaining=12;
                    @Override public int read() throws IOException {
                        if (remaining--<=0) throw new IOException("Acceptance collection interrupted");
                        return super.read();
                    }
                    @Override public int read(byte[] b,int offset,int length) throws IOException {
                        if (remaining<=0) throw new IOException("Acceptance collection interrupted");
                        int n=in.read(b,offset,Math.min(length,remaining)); remaining-=Math.max(n,0); return n;
                    }
                };
            };
        }
        return bean;
    }
}
