/**
 * Monitor: ทุก ~1 วินาที อ่านสถานะระบบแล้วพิมพ์ลง log
 *
 * ทุกค่าที่อ่านมาจากเมธอดที่ป้องกันด้วย synchronized / Semaphore อยู่แล้ว จึงปลอดภัย:
 *   ready     <- readyQueue.size()          (synchronized)
 *   running,completed <- statistics.snapshot()  (อ่านสองค่าในล็อกเดียว)
 *   resource  <- resources.status()         (Semaphore.availablePermits() ปลอดภัยในตัว)
 *
 * Main จะ interrupt Monitor เมื่อระบบจบ -> sleep โยน InterruptedException -> ออกจากลูป
 */
public class Monitor extends Thread {

    private static final long INTERVAL_MS = 1000;

    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;

    public Monitor(ReadyQueue readyQueue, ResourceManager resources,
                   Statistics statistics, ProjectLogger logger) {
        super("monitor");
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Thread.sleep(INTERVAL_MS);
                int[] snap = statistics.snapshot();   // {running, completed}
                logger.monitor(readyQueue.size(), snap[0], snap[1], resources.status());
            }
        } catch (InterruptedException e) {
            // Main สั่งหยุด: จบ Thread
        }
    }
}
