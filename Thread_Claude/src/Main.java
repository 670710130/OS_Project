import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * จุดเริ่มต้นของโปรแกรม: สร้างทุกชิ้นส่วน, เริ่ม Thread, รอจนจบ, สั่งหยุด, สรุปผล
 */
public class Main {

    public static void main(String[] args) {
        // ---------- 1. รับค่าจาก command line ----------
        Config config;
        try {
            config = Config.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("ผิดพลาด: " + e.getMessage());
            System.err.println();
            System.err.println(Config.USAGE);
            System.exit(1);
            return;
        }

        // ---------- 2. เริ่มจับเวลาและโหลด workload ----------
        ProjectLogger logger = new ProjectLogger();
        List<Job> jobs;
        try {
            jobs = WorkloadLoader.load(config.workloadPath);
        } catch (WorkloadFormatException e) {
            System.err.println("ไฟล์ workload ผิดรูปแบบ — " + e.getMessage());
            System.exit(1);
            return;
        } catch (java.io.IOException e) {
            System.err.println("เปิดไฟล์ \"" + config.workloadPath + "\" ไม่ได้");
            System.exit(1);
            return;
        }
        logger.systemStart(config);
        logger.systemEvent("โหลดงานได้ " + jobs.size() + " ชิ้น");

        // ---------- 3. สร้างส่วนประกอบของระบบ ----------
            //สร้าง ResourceManager จากจำนวน permit ใน config
        ResourceManager resources = new ResourceManager(config.printerPermits, config.databasePermits);
            //สร้าง ReadyQueue ตามนโยบายใน config
        ReadyQueue readyQueue = new ReadyQueue(config.policy);
            //สร้าง Statistics
        Statistics statistics = new Statistics();
            // arrivalQueue
        BlockingQueue<Job> arrivalQueue = new LinkedBlockingQueue<>();

        // ---------- 4. สร้างและเริ่ม Thread ----------
        // เริ่ม Worker/Scheduler/Monitor ก่อน เพื่อให้พร้อมรับงานตั้งแต่ต้น
        // แล้วค่อยเริ่ม JobGenerator (ลำดับนี้ไม่กระทบความถูกต้อง เพราะทุกตัวรอกันด้วย queue อยู่แล้ว)
        Worker[] workers = new Worker[config.workers];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Worker("worker-" + (i + 1), readyQueue, resources, statistics, logger);
            workers[i].start();
        }

        Scheduler scheduler = new Scheduler(arrivalQueue, readyQueue, logger);
        scheduler.start();

        Monitor monitor = new Monitor(readyQueue, resources, statistics, logger);
        monitor.start();

        JobGenerator generator = new JobGenerator(jobs, arrivalQueue, logger);
        generator.start();

        // ---------- 5. รอจนงานเสร็จครบ ----------
        // วิธีง่ายที่สุดสำหรับมือใหม่: join() ตามลำดับที่งานไหลผ่าน
        //   1) รอ JobGenerator ปล่อยงานครบ (มันจบเองเมื่อส่ง END ให้ Scheduler แล้ว)
        //   2) รอ Scheduler ปิด ReadyQueue (มันจบเองเมื่อได้ END จาก arrivalQueue)
        //   3) รอ Worker ทุกตัวทำงานที่เหลือใน ReadyQueue จนหมด (มันจบเองเมื่อ take() คืน null)
        try {
            generator.join();
            scheduler.join();
            for (Worker w : workers) {
                w.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // ถึงตรงนี้ทุกงานเสร็จแน่นอน เพราะ Worker ทุกตัวหยุดได้ก็ต่อเมื่อ ReadyQueue ปิดแล้วและว่างแล้ว

        // ---------- 6. สั่งหยุด Monitor ----------
        // Monitor เป็น Thread เดียวที่ไม่รู้จักหยุดเอง (มันวนตลอดไปทุก 1 วิ) ต้อง interrupt เอง
        monitor.interrupt();
        try {
            monitor.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ---------- 7. สรุปผล ----------
        long makespanMs = logger.now();   // ตอนนี้ทุกงานเสร็จแล้ว = เวลาปัจจุบันคือเวลาที่งานสุดท้ายเสร็จ
        statistics.printSummary(jobs, makespanMs);
        logger.systemStop(statistics.completedCount(), jobs.size());
    }
}
