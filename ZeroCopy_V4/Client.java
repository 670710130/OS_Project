import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class Client {

    static final String HOST = "localhost";
    static final int BUFFER = 64 * 1024;
    static int port;

    // เก็บข้อความผลทดสอบไว้ เพื่อบันทึกลงไฟล์ในโฟลเดอร์ logs
    static final StringBuilder report = new StringBuilder();

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !args[0].matches("\\d{1,5}")) {
            usage();
            return;
        }
        port = Integer.parseInt(args[0]);
        String command = args[1].toUpperCase();

        try {
            if (command.equals("LIST") && args.length == 2) {
                list();
            } else if (command.equals("INFO") && args.length == 3) {
                System.out.println(request("INFO " + args[2]));
            } else if (command.equals("HASH") && args.length == 3) {
                System.out.println(request("HASH " + args[2]));
            } else if (command.equals("GET") && isValidGet(args)) {
                String mode = (args.length == 6) ? args[5].toLowerCase() : "traditional";
                getRange(args[2], Long.parseLong(args[3]), Long.parseLong(args[4]), mode);
            } else if (args.length == 4 && isMode(args[2])
                    && (args[3].equals("1") || args[3].equals("3"))) {
                test(args[1], args[2].toLowerCase(), Integer.parseInt(args[3]));
            } else {
                usage();
            }
        } catch (IOException e) {
            // เช่น ERROR 404 file not found / ERROR 416 range is outside the file / Connection refused
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }

    static void usage() {
        System.out.println("Usage:");
        System.out.println("  java Client <port> LIST");
        System.out.println("  java Client <port> INFO <file>");
        System.out.println("  java Client <port> HASH <file>");
        System.out.println("  java Client <port> GET <file> <offset> <length> [traditional|nio]");
        System.out.println("  java Client <port> <file> <traditional|nio> <1|3>     run the test");
    }

    // Client ตรวจรูปแบบ GET เอง: offset/length ต้องเป็นตัวเลข >= 0 และ mode ต้องถูกต้อง
    static boolean isValidGet(String[] args) {
        if (args.length != 5 && args.length != 6) {
            return false;
        }
        boolean numbers = args[3].matches("\\d{1,18}") && args[4].matches("\\d{1,18}");
        return numbers && (args.length == 5 || isMode(args[5]));
    }

    static boolean isMode(String s) {
        return s.equalsIgnoreCase("traditional") || s.equalsIgnoreCase("nio");
    }

    // ---------- คำสั่ง protocol ----------

    // ส่ง 1 คำสั่ง แล้วคืนคำตอบ 1 บรรทัด (ใช้กับ INFO, HASH)
    // new Socket(HOST, port) เชื่อมต่อ Server
    static String request(String line) throws IOException {
        try (Socket s = new Socket(HOST, port)) {
            sendLine(s.getOutputStream(), line);
            return readLine(s.getInputStream());
        }
    }

    // LIST: แสดงทุกบรรทัดจนเจอ END
    static void list() throws IOException {
        try (Socket s = new Socket(HOST, port)) {
            sendLine(s.getOutputStream(), "LIST");
            InputStream in = s.getInputStream();
            String reply;
            do {
                reply = readLine(in);
                System.out.println(reply);
            } while (!reply.equals("END") && !reply.startsWith("ERROR"));
        }
    }

    // GET จาก command line: บันทึกช่วงเป็นไฟล์ แล้วแสดง hex 64 byte แรก
    static void getRange(String name, long offset, long length, String mode) throws IOException {
        Path out = Path.of("downloads", name + ".range-" + offset + "-" + (offset + length));
        createFile(out, length);
        try {
            fetchRange(name, offset, length, mode, out, 0);
        } catch (IOException e) {
            Files.deleteIfExists(out);   // Server ตอบ ERROR -> ไม่ทิ้งไฟล์ว่างไว้
            throw e;
        }
        System.out.println("OK " + length);
        System.out.println("Saved " + length + " bytes (" + mode + ") to " + out);
        printHex(out, offset);
    }

    static long getSize(String name) throws IOException {
        return Long.parseLong(expect(request("INFO " + name), "SIZE "));
    }

    static String getHash(String name) throws IOException {
        return expect(request("HASH " + name), "HASH ");
    }

    // ถ้าคำตอบไม่ขึ้นต้นตามที่คาด (เช่นเป็น ERROR ...) ให้ throw โดยใช้ข้อความของ Server
    static String expect(String reply, String prefix) throws IOException {
        if (!reply.startsWith(prefix)) {
            throw new IOException(reply);
        }
        return reply.substring(prefix.length());
    }

    // ---------- รับข้อมูล 1 ช่วง (ใช้ทั้งคำสั่ง GET และ worker ในโหมดทดสอบ) ----------

    // ขอช่วง offset/length จาก Server แล้วเขียนลง target เริ่มที่ตำแหน่ง writeAt
    static void fetchRange(String name, long offset, long length, String mode,
                           Path target, long writeAt) throws IOException {
        String request = "GET " + name + " " + offset + " " + length + " " + mode;
        if (mode.equals("nio")) {
            fetchNio(request, length, target, writeAt);
        } else {
            fetchTraditional(request, length, target, writeAt);
        }
    }

    // Traditional: socket stream -> byte[] -> RandomAccessFile (แต่ละ worker เปิดของตัวเอง)
    static void fetchTraditional(String request, long length, Path target, long writeAt)
            throws IOException {
        try (Socket s = new Socket(HOST, port);
             RandomAccessFile file = new RandomAccessFile(target.toFile(), "rw")) {

            sendLine(s.getOutputStream(), request);
            InputStream in = s.getInputStream();
            expect(readLine(in), "OK ");

            file.seek(writeAt);
            byte[] buffer = new byte[BUFFER];
            long left = length;
            while (left > 0) {
                int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (n == -1) {
                    throw new IOException("connection closed early");
                }
                file.write(buffer, 0, n);
                left -= n;
            }
        }
    }

    // NIO: SocketChannel -> FileChannel.transferFrom (แต่ละ worker เปิด channel ของตัวเอง)
    static void fetchNio(String request, long length, Path target, long writeAt)
            throws IOException {
        try (SocketChannel socket = SocketChannel.open(new InetSocketAddress(HOST, port));
             FileChannel file = FileChannel.open(target, StandardOpenOption.WRITE)) {

            // header เป็นข้อความ จึงอ่านทีละ byte ให้หยุดตรง \n พอดี
            // byte หลังจากนั้นเป็นข้อมูลไฟล์ทั้งหมด และ transferFrom จะรับต่อ
            sendLine(Channels.newOutputStream(socket), request);
            expect(readLine(Channels.newInputStream(socket)), "OK ");

            long done = 0;
            while (done < length) {
                long n = file.transferFrom(socket, writeAt + done, length - done);
                if (n <= 0) {
                    throw new IOException("connection closed early");
                }
                done += n;
            }
        }
    }

    // ---------- โหมดทดสอบ ----------

    static void say(String text) {
        System.out.println(text);
        report.append(text).append(System.lineSeparator());
    }

    // ทดสอบ 1 และ 10 workers อย่างละ runs รอบ แล้วบันทึกผลลง logs
    static void test(String name, String mode, int runs) throws Exception {
        int[] workerCounts = {1, 10};
        double[] total = new double[workerCounts.length];

        double mb = getSize(name) / (1024.0 * 1024.0);
        String expectedHash = getHash(name);

        say("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        say(String.format("File: %s (%.0f MB)  mode: %s (client + server)  runs: %d", name, mb, mode, runs));
        say("Server SHA-256: " + expectedHash);

        // อุ่นเครื่อง 1 รอบ (ไม่นับ) ให้ไฟล์เข้า Page Cache และ JVM อุ่นแล้ว
        say("Warm-up (not counted)...");
        download(name, 10, mode, expectedHash);

        say("run  mode         workers  time(s)  MB/s     check");
        for (int run = 1; run <= runs; run++) {
            for (int w = 0; w < workerCounts.length; w++) {
                double sec = download(name, workerCounts[w], mode, expectedHash);
                total[w] += sec;
                say(String.format("%-4d %-12s %-8d %-8.2f %-8.1f size+hash OK",
                        run, mode, workerCounts[w], sec, mb / sec));
            }
        }

        if (runs > 1) {
            say("");
            say("Average of " + runs + " runs");
            say("mode         workers  time(s)  MB/s");
            for (int w = 0; w < workerCounts.length; w++) {
                double avg = total[w] / runs;
                say(String.format("%-12s %-8d %-8.2f %.1f", mode, workerCounts[w], avg, mb / avg));
            }
        }

        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path logFile = Path.of("logs", mode + "-" + runs + "run-" + time + ".txt");
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, report.toString());
        System.out.println();
        System.out.println("Saved results to " + logFile);
    }

    // โหลดทั้งไฟล์ด้วย workers ตัว ตรวจ size + SHA-256 แล้วคืนเวลาที่ใช้ (วินาที)
    static double download(String name, int workers, String mode, String expectedHash)
            throws Exception {
        long size = getSize(name);
        Path out = Path.of("downloads", name);
        createFile(out, size);

        long part = size / workers;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<?>> tasks = new ArrayList<>();

        long start = System.nanoTime();
        try {
            for (int i = 0; i < workers; i++) {
                long offset = i * part;
                long length = (i == workers - 1) ? size - offset : part;   // ช่วงสุดท้ายรับส่วนที่เหลือ
                tasks.add(pool.submit(() -> {
                    fetchRange(name, offset, length, mode, out, offset);   // เขียนตาม offset ไม่ต้อง merge
                    return null;
                }));
            }
            for (Future<?> t : tasks) {
                try {
                    t.get();
                } catch (ExecutionException e) {
                    throw new IOException("worker failed: " + e.getCause().getMessage());
                }
            }
        } finally {
            pool.shutdown();
        }
        double sec = (System.nanoTime() - start) / 1_000_000_000.0;

        // ตรวจความถูกต้อง (ไม่นับเวลา)
        if (Files.size(out) != size) {
            throw new IOException("size of downloaded file is wrong");
        }
        if (!sha256(out).equals(expectedHash)) {
            throw new IOException("SHA-256 of downloaded file differs from the server's");
        }
        return sec;
    }

    // ---------- ตัวช่วย ----------

    // สร้างไฟล์ใหม่ขนาด size (ลบของเก่าก่อน กันข้อมูลรอบก่อนค้างจนตรวจไม่เจอว่าโหลดผิด)
    static void createFile(Path file, long size) throws IOException {
        Files.createDirectories(file.getParent());
        Files.deleteIfExists(file);
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "rw")) {
            f.setLength(size);
        }
    }

    // แสดง 64 byte แรกเป็น hex บรรทัดละ 16 byte โดยตัวเลขซ้ายคือตำแหน่งในไฟล์ต้นฉบับ
    static void printHex(Path file, long offset) throws IOException {
        byte[] head = new byte[64];
        int n;
        try (InputStream in = Files.newInputStream(file)) {
            n = in.readNBytes(head, 0, head.length);
        }
        System.out.println("First " + n + " bytes (hex):");
        for (int i = 0; i < n; i++) {
            if (i % 16 == 0) {
                System.out.printf("%08X  ", offset + i);
            }
            System.out.printf("%02X ", head[i]);
            if (i % 16 == 15 || i == n - 1) {
                System.out.println();
            }
        }
    }

    static String sha256(Path file) throws Exception {
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

    // อ่าน 1 บรรทัดทีละ byte จนเจอ \n (ไม่ใช้ BufferedReader เพราะจะอ่านข้อมูลไฟล์ที่ตามมาไปด้วย)
    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            buf.write(b);
        }
        if (b == -1) {
            throw new IOException("server closed the connection");
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    static void sendLine(OutputStream out, String text) throws IOException {
        out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}