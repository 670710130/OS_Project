import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.concurrent.*;

public class Server {

    // โฟลเดอร์ที่ Server แชร์ไฟล์ (สัมพันธ์กับที่รันโปรแกรม)
    static final Path FILES = Path.of("files");
    static final int BUFFER = 64 * 1024;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        Files.createDirectories(FILES);

        // Thread Pool: รับได้พร้อมกันสูงสุด 20 connection
        ExecutorService pool = Executors.newFixedThreadPool(20);

        // ใช้ ServerSocketChannel เพราะโหมด nio ต้อง transferTo() ลง SocketChannel
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(port));
            System.out.println("Server listening on port " + port);
            System.out.println("Sharing folder: " + FILES.toAbsolutePath());

            while (true) {
                SocketChannel client = server.accept();
                pool.submit(() -> handle(client));
            }
        }
    }

    // 1 connection = รับคำสั่ง 1 บรรทัด แล้วตอบกลับ
    private static void handle(SocketChannel client) {
        try (client;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(Channels.newInputStream(client), StandardCharsets.UTF_8));
             OutputStream out = Channels.newOutputStream(client)) {

            String line = in.readLine();
            if (line == null) {
                return;
            }
            System.out.println("<- " + line);

            String[] parts = line.trim().split("\\s+");
            String command = parts[0].toUpperCase();

            if (command.equals("LIST") && parts.length == 1) {
                doList(out);
            } else if (command.equals("INFO") && parts.length == 2) {
                doInfo(out, parts[1]);
            } else if (command.equals("HASH") && parts.length == 2) {
                doHash(out, parts[1]);
            } else if (command.equals("GET")) {
                doGet(client, out, parts);
            } else {
                send(out, "ERROR 400 bad request");
            }

        } catch (Exception e) {
            System.out.println("connection error: " + e.getMessage());
        }
    }

    // ---------- คำสั่งใน protocol ----------

    // LIST -> FILE <name> <size> ... แล้วปิดท้ายด้วย END
    private static void doList(OutputStream out) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(FILES)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    send(out, "FILE " + p.getFileName() + " " + Files.size(p));
                }
            }
        }
        send(out, "END");
    }

    // INFO <file> -> SIZE <bytes>
    private static void doInfo(OutputStream out, String name) throws IOException {
        Path file = findFile(name);
        if (file == null) {
            send(out, "ERROR 404 file not found");
            return;
        }
        send(out, "SIZE " + Files.size(file));
    }

    // HASH <file> -> HASH <sha256>
    private static void doHash(OutputStream out, String name) throws Exception {
        Path file = findFile(name);
        if (file == null) {
            send(out, "ERROR 404 file not found");
            return;
        }
        send(out, "HASH " + sha256(file));
    }

    // GET <file> <offset> <length> [traditional|nio] -> OK <length> แล้วตามด้วย byte ดิบ
    private static void doGet(SocketChannel client, OutputStream out, String[] parts)
            throws IOException {
        if (parts.length < 4 || parts.length > 5) {
            send(out, "ERROR 400 usage: GET <file> <offset> <length> [traditional|nio]");
            return;
        }

        String mode = (parts.length == 5) ? parts[4].toLowerCase() : "traditional";
        if (!mode.equals("traditional") && !mode.equals("nio")) {
            send(out, "ERROR 400 mode must be traditional or nio");
            return;
        }

        long offset;
        long length;
        try {
            offset = Long.parseLong(parts[2]);
            length = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            send(out, "ERROR 400 offset and length must be numbers");
            return;
        }

        Path file = findFile(parts[1]);
        if (file == null) {
            send(out, "ERROR 404 file not found");
            return;
        }

        long size = Files.size(file);
        if (offset < 0 || length < 0 || offset > size || length > size - offset) {
            send(out, "ERROR 416 range is outside the file");
            return;
        }

        send(out, "OK " + length);
        if (mode.equals("nio")) {
            sendNio(file, offset, length, client);
        } else {
            sendTraditional(file, offset, length, out);
        }
        System.out.println("-> (payload sent: " + length + " bytes, " + mode + ")");
    }

    // ---------- วิธีส่งข้อมูล 2 แบบ ----------

    // Traditional: อ่านไฟล์ใส่ byte[] ของเรา แล้วเขียนลง socket (ข้อมูลผ่าน user space)
    private static void sendTraditional(Path file, long offset, long length, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[BUFFER];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(offset);
            long left = length;
            while (left > 0) {
                int n = raf.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (n == -1) {
                    throw new IOException("file ended early");
                }
                out.write(buffer, 0, n);
                left -= n;
            }
        }
        out.flush();
    }

    // NIO: ให้ OS ย้ายข้อมูลจากไฟล์ลง socket เอง (transferTo)
    private static void sendNio(Path file, long offset, long length, SocketChannel client)
            throws IOException {
        try (FileChannel source = FileChannel.open(file, StandardOpenOption.READ)) {
            long sent = 0;
            while (sent < length) {
                long n = source.transferTo(offset + sent, length - sent, client);
                if (n <= 0) {
                    throw new IOException("transferTo made no progress");
                }
                sent += n;
            }
        }
    }

    // ---------- ตัวช่วย ----------

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                md.update(buffer, 0, n);
            }
        }
        return String.format("%064x", new BigInteger(1, md.digest()));
    }

    // หาไฟล์ในโฟลเดอร์ files ถ้าไม่มีหรือชื่อไม่ปลอดภัย (เช่น ..\secret.txt) จะคืน null
    private static Path findFile(String name) {
        try {
            boolean safeName = Path.of(name).getFileName().toString().equals(name);
            Path file = FILES.resolve(name);
            if (safeName && Files.isRegularFile(file)) {
                return file;
            }
        } catch (InvalidPathException e) {
            // ชื่อมีตัวอักษรที่ใช้ไม่ได้ เช่น ? หรือ * บน Windows
        }
        return null;
    }

    // ส่ง 1 บรรทัด ลงท้ายด้วย \n เสมอ (ไม่ใช้ println เพราะ Windows จะใส่ \r\n)
    private static void send(OutputStream out, String text) throws IOException {
        System.out.println("-> " + text);
        out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}