import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * ปล่อยงานเข้าสู่ระบบตามเวลา arrivalMs
 *
 * ส่งงานให้ Scheduler ผ่าน arrivalQueue (BlockingQueue)
 * ห้ามใส่ ReadyQueue โดยตรง (โจทย์กำหนด)
 *
 * ปล่อยครบแล้ว -> ใส่ Scheduler.END ต่อท้าย เพื่อบอกว่า "หมดแล้ว" (เทคนิค poison pill)
 */
public class JobGenerator extends Thread {

    private final List<Job> jobs;
    private final BlockingQueue<Job> arrivalQueue;
    private final ProjectLogger logger;

    public JobGenerator(List<Job> jobs, BlockingQueue<Job> arrivalQueue, ProjectLogger logger) {
        super("generator");
        // รายการจาก WorkloadLoader ไม่ได้เรียงตามเวลา จึงคัดลอกมาเรียงเอง
        // (ไม่แก้ list ต้นฉบับ เพราะ Main ยังใช้ต่อ)
        this.jobs = new ArrayList<>(jobs);
        this.jobs.sort(Comparator.comparingLong((Job j) -> j.arrivalMs)
                                 .thenComparingInt(j -> j.sequence));
        this.arrivalQueue = arrivalQueue;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            for (Job job : jobs) {
                // รอจนถึงเวลา arrivalMs (นับจากนาฬิกาเดียวกับ log)
                long waitMs = job.arrivalMs - logger.now();
                if (waitMs > 0) {
                    Thread.sleep(waitMs);
                }

                job.actualArrivalMs = logger.now();   // เวลาเข้าระบบ "จริง"
                logger.jobArrived(job);
                arrivalQueue.put(job);                // ส่งต่อให้ Scheduler
            }
            arrivalQueue.put(Scheduler.END);          // ปล่อยครบแล้ว
        } catch (InterruptedException e) {
            // ถูกสั่งหยุด: จบ Thread
        }
    }
}
