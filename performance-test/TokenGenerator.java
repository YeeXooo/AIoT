import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Leming WebRunner 性能测试——JWT Token 批量生成工具。
 * 从 PKCS12 KeyStore 读取主密钥，批量签发 Token 并输出为 CSV。
 *
 * Usage:
 *   java ... TokenGenerator <keyStorePath> <ksPwd> <alias> <keyPwd> <role1> <count1> [<role2> <count2> ...]
 *
 * Examples:
 *   # 单个 Token
 *   java ... TokenGenerator ks.p12 pwd alias keyPwd myUser FAMILY
 *
 *   # 批量 CSV：FAMILY×50, MANAGER×20, RESCUE×10
 *   java ... TokenGenerator ks.p12 pwd alias keyPwd FAMILY 50 MANAGER 20 RESCUE 10 > tokens.csv
 */
public class TokenGenerator {

    private static final long ACCESS_VALIDITY_SEC = 3600;

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: TokenGenerator <ksPath> <ksPwd> <alias> <keyPwd> <accountId> <role>");
            System.err.println("   or: TokenGenerator <ksPath> <ksPwd> <alias> <keyPwd> <role1> <count1> [<role2> <count2> ...]");
            System.exit(1);
        }

        String ksPath = args[0];
        String ksPwd  = args[1];
        String alias  = args[2];
        String keyPwd = args[3];

        // Load master key
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(new File(ksPath))) {
            ks.load(fis, ksPwd.toCharArray());
        }
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
                ks.getEntry(alias, new KeyStore.PasswordProtection(keyPwd.toCharArray()));
        if (entry == null) {
            System.err.println("ERROR: alias not found: " + alias);
            System.exit(1);
        }
        SecretKey signKey = Keys.hmacShaKeyFor(entry.getSecretKey().getEncoded());

        // Determine mode: single or batch
        // If arg[4] can be parsed as int, it's batch mode (role1 count1 role2 count2 ...)
        boolean batchMode;
        try {
            Integer.parseInt(args[4]);
            batchMode = false;  // arg[4] is a role string like "FAMILY"
        } catch (NumberFormatException e) {
            batchMode = true;   // check further
        }

        // Actually simpler: if args[5] exists and can be int, it's batch mode
        if (args.length == 6 && !args[4].matches("\\d+")) {
            // Single token mode: <ksPath> <ksPwd> <alias> <keyPwd> <account> <role>
            String account = args[4];
            String role = args[5];
            String accessToken = createToken(signKey, account, role, ACCESS_VALIDITY_SEC, "access");
            System.out.println("# Master Key hex: " + bytesToHex(entry.getSecretKey().getEncoded()));
            System.out.println("# Account: " + account + "  Role: " + role);
            System.out.println("# ACCESS_TOKEN (valid " + ACCESS_VALIDITY_SEC + "s):");
            System.out.println(accessToken);
        } else if (args.length >= 6) {
            // Batch CSV mode: <ksPath> <ksPwd> <alias> <keyPwd> <role1> <count1> [<role2> <count2> ...]
            System.out.println("accountId,role,accessToken");
            int i = 4;
            while (i < args.length - 1) {
                String role = args[i];
                int count = Integer.parseInt(args[i + 1]);
                for (int j = 1; j <= count; j++) {
                    String account = String.format("perf-%s-%03d", role.toLowerCase(), j);
                    String accessToken = createToken(signKey, account, role, ACCESS_VALIDITY_SEC, "access");
                    System.out.println(account + "," + role + "," + accessToken);
                }
                i += 2;
            }
        } else {
            // Legacy single mode: <ksPath> <ksPwd> <alias> <keyPwd> <account> <role>
            String account = args.length >= 5 ? args[4] : "test-user-001";
            String role = args.length >= 6 ? args[5] : "FAMILY";
            String accessToken = createToken(signKey, account, role, ACCESS_VALIDITY_SEC, "access");
            String refreshToken = createToken(signKey, account, role, 86400, "refresh");
            System.out.println("# Master Key (hex): " + bytesToHex(entry.getSecretKey().getEncoded()));
            System.out.println("# Account: " + account + "  Role: " + role);
            System.out.println("# ACCESS_TOKEN  (valid " + ACCESS_VALIDITY_SEC + "s):");
            System.out.println(accessToken);
            System.out.println("# REFRESH_TOKEN (valid 86400s):");
            System.out.println(refreshToken);
        }
    }

    private static String createToken(SecretKey key, String account, String role, long validitySec, String type) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("type", type);
        return Jwts.builder()
                .claims(claims)
                .subject(account)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySec)))
                .signWith(key)
                .compact();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
