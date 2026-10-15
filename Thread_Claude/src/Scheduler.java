import java.util.concurrent.BlockingQueue;

/**
 * รับงานจาก arrivalQueue แล้วส่งเข้า ReadyQueue
 *
 * take() ของ BlockingQueue: ถ้าคิวว่างจะหลับรอ ไม่กิน CPU
 * เมื่อได้ END (ตัวบอกว่าหมดแล้ว) -> ปิด ReadyQueue แล้วจบ Thread
 */
public class Scheduler extends Thread {

    /** งานพิเศษ "ไม่ใช่งานจริง" ใช้เป็นสัญญาณว่า JobGenerator ปล่อยงานครบแล้ว (poison pill) */
    public static final Job END = new Job("END", 0, 1, 0, ResourceType.NONE, 0, -1);

    private final BlockingQueue<Job> arrivalQueue;
    private final ReadyQueue readyQueue;
    private final ProjectLogger logger;

    public Scheduler(BlockingQueue<Job> arrivalQueue, ReadyQueue readyQueue, ProjectLogger logger) {
        super("scheduler");
        this.arrivalQueue = arrivalQueue;
        this.readyQueue = readyQueue;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Job job = arrivalQueue.take();   // รอจนมีงานเข้ามา
                if (job == END) {
                    break;                       // ไม่มีงานใหม่แล้ว
                }
                readyQueue.add(job);
                logger.systemEvent("job=" + job.id + " READY");
            }
        } catch (InterruptedException e) {
            // ถูกสั่งหยุด
        } finally {
            // ไม่ว่าจบแบบไหน ต้องปิด ReadyQueue เพื่อให้ Worker ที่รออยู่เลิกรอ
            readyQueue.close();
        }
    }
}
