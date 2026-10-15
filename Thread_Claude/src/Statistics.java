import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * เก็บตัวนับและผลลัพธ์ของงานที่เสร็จ แล้วสรุปตอนจบโปรแกรม
 *
 * ข้อมูลร่วมในคลาสนี้ (หลาย Worker แก้พร้อมกัน, Monitor อ่านพร้อมกัน):
 *   running   = จำนวนงานที่ Worker กำลังทำ (รวมช่วงรอ resource)
 *   completed = จำนวนงานที่เสร็จแล้ว
 *   finished  = รายการงานที่เสร็จแล้ว (ไว้คำนวณค่าเฉลี่ย)
 *
 * ป้องกันด้วย synchronized ทุกเมธอด -> ทีละ Thread เท่านั้น จึงไม่มี race condition
 */
public class Statistics {

    private int running = 0;
    private int completed = 0;
    private final List<Job> finished = new ArrayList<>();

    /** Worker เรียกตอนเริ่มทำงาน: running + 1 */
    public synchronized void jobStartedRunning() {
        running++;
    }

    /** Worker เรียกเมื่องานถูกยกเลิกกลางคัน (เช่นถูก interrupt): running - 1 */
    public synchronized void jobAborted() {
        running--;
    }

    /** บันทึกว่างานเสร็จ: running - 1, completed + 1 (ทำพร้อมกันในล็อกเดียว) */
    public synchronized void recordCompletion(Job job) {
        running--;
        completed++;
        finished.add(job);
    }

    /** จำนวนงานที่เสร็จแล้ว */
    public synchronized int completedCount() {
        return completed;
    }

    /**
     * ภาพถ่าย (snapshot) ของ {running, completed} ณ ขณะเดียวกัน
     * อ่านสองค่าในล็อกเดียว จึงไม่มีทางเห็นค่าที่ Worker แก้ไปครึ่งเดียว
     */
    public synchronized int[] snapshot() {
        return new int[] { running, completed };
    }

    /** พิมพ์ตารางสรุปผลตอนจบ (เรียกหลังทุก Thread หยุดแล้ว) */
    public void printSummary(List<Job> allJobs, long makespanMs) {
        // คัดลอกรายการออกมาก่อน แล้วค่อยคำนวณนอกล็อก
        List<Job> done;
        synchronized (this) {
            done = new ArrayList<>(finished);
        }
        done.sort(Comparator.comparingInt(j -> j.sequence));

        System.out.println();
        System.out.println("===== Report (Check : TAT = WT + work + RW + res, diff should approximate 0) =====");
        System.out.println(String.format("%-6s %4s %7s %7s %7s %7s %7s %5s",
                "job", "pri", "WT", "work", "RW", "res", "TAT", "diff"));

        long sumWT = 0, sumTAT = 0, sumRW = 0;
        int resourceJobs = 0;

        for (Job j : done) {
            long wt = j.waitingTime();
            long tat = j.turnaroundTime();
            long diff = tat - (wt + j.workMs + j.resourceWaitMs + j.resourceMs);
            System.out.println(String.format("%-6s %4d %7d %7d %7d %7d %7d %5d",
                    j.id, j.priority, wt, j.workMs, j.resourceWaitMs, j.resourceMs, tat, diff));

            sumWT += wt;
            sumTAT += tat;
            if (j.resource != ResourceType.NONE) {   // RW เฉลี่ยเฉพาะงานที่ใช้ resource
                sumRW += j.resourceWaitMs;
                resourceJobs++;
            }
        }

        long avgWT = average(sumWT, done.size());
        long avgTAT = average(sumTAT, done.size());
        long avgRW = average(sumRW, resourceJobs);
        double throughput = makespanMs > 0 ? done.size() / (makespanMs / 1000.0) : 0.0;

        System.out.println();
        System.out.println("===== SUMMARY =====");
        System.out.println("completed        = " + done.size() + "/" + allJobs.size());
        System.out.println("avg Waiting Time = " + avgWT + " ms");
        System.out.println("avg Turnaround   = " + avgTAT + " ms");
        System.out.println("avg Resource Wait= " + avgRW + " ms (Average from " + resourceJobs + " request resource)");
        System.out.println(String.format(Locale.US,
                "Throughput       = %.2f jobs/second (makespan = %d ms)", throughput, makespanMs));
        System.out.println(String.format(Locale.US,
                "RESULT avgWT=%d avgTAT=%d throughput=%.2f avgRW=%d", avgWT, avgTAT, throughput, avgRW));
    }

    /** ค่าเฉลี่ยปัดเป็นจำนวนเต็ม (ป้องกันหารด้วย 0) */
    private static long average(long sum, int count) {
        if (count == 0) {
            return 0;
        }
        return Math.round((double) sum / count);
    }
}
