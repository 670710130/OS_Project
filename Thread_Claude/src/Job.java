/**
 * ข้อมูลของงานหนึ่งชิ้น
 *
 * แบ่งเป็น 2 ส่วน:
 *   ส่วนที่ 1 (final)    : ข้อมูลจากไฟล์ CSV กำหนดครั้งเดียว ไม่เปลี่ยน -> Thread ไหนอ่านก็ปลอดภัย
 *   ส่วนที่ 2 (volatile) : ค่าวัดผล ถูกเขียนระหว่างที่ระบบทำงาน
 */
public class Job {

    // ================= ส่วนที่ 1: ข้อมูลจากไฟล์ workload =================

    /** รหัสงาน เช่น J01 */
    public final String id;

    /** เวลาที่งานควรเข้าระบบ (ms นับจากเริ่มโปรแกรม) */
    public final long arrivalMs;

    /** ความสำคัญ: 1 = สูงสุด ตัวเลขยิ่งมาก ยิ่งสำคัญน้อย */
    public final int priority;

    /** เวลางานหลัก (ms) */
    public final long workMs;

    /** ทรัพยากรที่ต้องใช้ (NONE / PRINTER / DATABASE) */
    public final ResourceType resource;

    /** เวลาที่ถือทรัพยากร (ms) */
    public final long resourceMs;

    /** ลำดับของงานนี้ในไฟล์ CSV เริ่มจาก 0 (ใช้เป็นตัวตัดสินตอน priority เท่ากัน) */
    public final int sequence;

    public Job(String id, long arrivalMs, int priority, long workMs,
               ResourceType resource, long resourceMs, int sequence) {
        this.id = id;
        this.arrivalMs = arrivalMs;
        this.priority = priority;
        this.workMs = workMs;
        this.resource = resource;
        this.resourceMs = resourceMs;
        this.sequence = sequence;
    }

    // ================= ส่วนที่ 2: ค่าวัดผล =================
    //
    // ทุกค่าเป็นเวลาจาก logger.now() (ms) นาฬิกาเดียวกับใน log
    //
    // ทำไมต้อง volatile?
    //   - Thread หนึ่งเขียน (เช่น Worker) แต่อีก Thread อ่าน (เช่น Main ตอนสรุปผล)
    //   - volatile รับประกันว่าอีก Thread จะเห็นค่าล่าสุดเสมอ
    //   - ปลอดภัยพอ เพราะแต่ละช่องมี Thread เขียนทีละตัวตามลำดับ (ไม่มีสอง Thread เขียนช่องเดียวกันพร้อมกัน)

    /** เวลาที่งานเข้าระบบ "จริง" (เขียนโดย JobGenerator) */
    public volatile long actualArrivalMs;

    /** เวลาที่ Worker เริ่มทำงานนี้ (เขียนโดย Worker) */
    public volatile long startMs;

    /** เวลาที่งานเสร็จ (เขียนโดย Worker) */
    public volatile long completeMs;

    /** เวลาที่รอ resource รวม (ms) เป็น 0 ถ้าไม่ใช้ resource (เขียนโดย Worker) */
    public volatile long resourceWaitMs;

    /** Waiting Time = เวลาเริ่มทำ - เวลาเข้าระบบจริง */
    public long waitingTime() {
        return startMs - actualArrivalMs;
    }

    /** Turnaround Time = เวลาเสร็จ - เวลาเข้าระบบจริง */
    public long turnaroundTime() {
        return completeMs - actualArrivalMs;
    }

    @Override
    public String toString() {
        return String.format("%s(priority=%d, work=%dms, %s)",
                id, priority, workMs,
                resource == ResourceType.NONE ? "no resource"
                        : resource + " " + resourceMs + "ms");
    }
}
