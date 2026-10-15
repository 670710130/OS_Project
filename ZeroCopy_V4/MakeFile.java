import java.io.*;
import java.nio.file.*;
import java.util.Random;

// สร้างไฟล์ทดสอบที่มีข้อมูลสุ่ม (ไม่ซ้ำกันทั้งไฟล์ เพื่อให้ตรวจเจอถ้า Client เขียนผิดตำแหน่ง)
public class MakeFile {

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage: java MakeFile <path> <size in MB>");
            return;
        }

        Path path = Path.of(args[0]);
        long mb = Long.parseLong(args[1]);

        Files.createDirectories(path.toAbsolutePath().getParent());

        Random random = new Random(42);               // seed คงที่ ได้ไฟล์เดิมทุกครั้ง
        byte[] chunk = new byte[1024 * 1024];         // ก้อนละ 1 MB

        try (OutputStream out = Files.newOutputStream(path)) {
            for (long i = 0; i < mb; i++) {
                random.nextBytes(chunk);              // สุ่มใหม่ทุกก้อน
                out.write(chunk);
            }
        }
        System.out.println("Created " + path + " (" + mb + " MB)");
    }
}
