import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.jeecg.common.util.PasswordUtil;

/** Test account seeder only; uses the application's unchanged password implementation. */
public class AccountPassword {
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while ((line = input.readLine()) != null) {
            String[] fields = line.split("\t");
            System.out.println(PasswordUtil.encrypt(fields[0], fields[1], fields[2]));
        }
    }
}
