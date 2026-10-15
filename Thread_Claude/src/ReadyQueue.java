import java.util.ArrayList;
import java.util.List;

/**
 * คิวงานที่พร้อมถูกหยิบไปทำ (ตัวกลางระหว่าง Scheduler กับ Worker)
 *
 * ใครเรียกอะไร:
 *   Scheduler  -> add(job)   ใส่งานเข้าคิว
 *   Worker     -> take()     หยิบงานออกไปทำ (หลายตัวพร้อมกัน)
 *   Scheduler  -> close()    บอกว่า "จะไม่มีงานใหม่แล้ว"
 *   Monitor    -> size()     ดูว่ารออยู่กี่งาน
 *
 * ป้องกันข้อมูลร่วมอย่างไร?
 *   ทุกเมธอดเป็น synchronized = ทีละ Thread เท่านั้นที่เข้าได้ (ล็อกที่ตัว object นี้)
 *
 * รอโดยไม่กิน CPU อย่างไร?
 *   wait() = Thread หลับ และปล่อยล็อก จนกว่าจะมีคนเรียก notifyAll() มาปลุก
 */
public class ReadyQueue {

    private final Config.Policy policy; // จาก config
    private final List<Job> jobs = new ArrayList<>();   // งานที่รออยู่ (เรียงตามลำดับที่เข้าคิว)
    private boolean closed = false;                     // true = จะไม่มีงานใหม่เข้ามาอีกแล้ว

    public ReadyQueue(Config.Policy policy) {
        this.policy = policy;
    }

    /** ใส่งานเข้าคิว เรียกโดย Scheduler Thread */
    public synchronized void add(Job job) {
        jobs.add(job);
        notifyAll();   // ปลุก Worker ที่หลับรออยู่
    }

    /**
     * บอกว่าจะไม่มีงานใหม่แล้ว เรียกโดย Scheduler ตอนจบ
     * Worker ที่รออยู่จะถูกปลุกและรู้ว่าถึงเวลาเลิก
     */
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    /**
     * หยิบงานถัดไปตามนโยบาย เรียกโดย Worker Thread
     *
     * @return งานที่หยิบได้ หรือ null ถ้าคิวว่างและปิดแล้ว (= Worker ควรเลิกทำงาน)
     */
    public synchronized Job take() throws InterruptedException {
        while (jobs.isEmpty()) {
            if (closed) {
                return null;   // ไม่มีงานเหลือ และไม่มีงานใหม่มาแน่ ๆ
            }
            wait();            // หลับรอ (ไม่กิน CPU)
        }
        int best = pickBestIndex(); // ดู policy ว่าเป็นcแบบไหน แล้วหยิบ
        return jobs.remove(best);   // remove(ตำแหน่ง) เลื่อนตัวที่เหลือมาด้านหน้า คืนงานที่ถูกเอาออก
    }

    /** จำนวนงานที่รออยู่ตอนนี้ ใช้โดย Monitor */
    public synchronized int size() {
        return jobs.size();
    }

    /** เลือก "ตำแหน่ง" ของงานที่ควรถูกหยิบก่อน TIE BREAK */
    private int pickBestIndex() {

        /// FCFS เลือกตำแหน่ง 0 อยู่แล้ว
        if (policy == Config.Policy.FCFS) {
            return 0;   // งานที่เข้าคิวก่อน อยู่หน้าสุดของ list เสมอ
        }

        // PRIORITY: หาตัวที่ดีที่สุดโดยไล่เทียบทีละตัว
        int best = 0;
        for (int i = 1; i < jobs.size(); i++) {
            if (isBetter(jobs.get(i), jobs.get(best))) {
                best = i;
            }
        }
        return best;
    }

    /**
     * a ควรมาก่อน b หรือไม่? (กติกา tie-break อยู่ตรงนี้)
     *   1) priority เลขน้อยกว่า ชนะ
     *   2) ถ้าเท่ากัน: arrivalMs น้อยกว่า (มาก่อน) ชนะ
     *   3) ถ้ายังเท่ากัน: sequence น้อยกว่า (อยู่บนสุดในไฟล์) ชนะ
     * ทุกข้อใช้ข้อมูลของ Job เอง ไม่เกี่ยวกับว่า Thread ไหนมาถึงก่อน
     */
    private static boolean isBetter(Job a, Job b) {
        if (a.priority != b.priority) {
            return a.priority < b.priority;
        }
        if (a.arrivalMs != b.arrivalMs) {
            return a.arrivalMs < b.arrivalMs;
        }
        return a.sequence < b.sequence;
    }
}
