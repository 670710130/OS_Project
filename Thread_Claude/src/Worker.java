/**
 * Worker: ดึงงานจาก ReadyQueue ไปทำจนเสร็จ ทีละงาน
 *
 * วนไปเรื่อย ๆ จนกว่า take() จะคืน null (= คิวว่างและปิดแล้ว) จึงเลิก
 */
public class Worker extends Thread {

    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;

    public Worker(String name, ReadyQueue readyQueue, ResourceManager resources,
                  Statistics statistics, ProjectLogger logger) {
        super(name);
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Job job = readyQueue.take();   // หลับรอถ้ายังไม่มีงาน
                if (job == null) {
                    break;                     // ไม่มีงานเหลือแล้ว เลิกงาน
                }
                processJob(job);
            }
        } catch (InterruptedException e) {
            logger.systemEvent(getName() + " ถูก interrupt จึงหยุดทำงาน");
        }
    }

    /** ทำงานหนึ่งชิ้นตามลำดับที่โจทย์กำหนด */
    private void processJob(Job job) throws InterruptedException {
        boolean finished = false;
        statistics.jobStartedRunning();          // running + 1
        try {
            // ขั้น 1: บันทึกเวลาเริ่ม
            job.startMs = logger.now();
            logger.jobStarted(job);

            // ขั้น 2: งานหลัก (จำลองด้วย sleep)
            Thread.sleep(job.workMs);
            logger.workFinished(job);

            // ขั้น 3-6: ใช้ resource (ถ้ามี)
            if (job.resource != ResourceType.NONE) {
                useResource(job);
            }

            // ขั้น 7: บันทึกเวลาเสร็จ และส่งผลให้ Statistics
            job.completeMs = logger.now();
            logger.jobCompleted(job);
            statistics.recordCompletion(job);    // running - 1, completed + 1
            finished = true;
        } finally {
            if (!finished) {
                statistics.jobAborted();         // งานไม่จบ (เช่นถูก interrupt) ต้องลด running คืน
            }
        }
    }

    /** ขอ resource -> ถือไว้ -> คืน */
    private void useResource(Job job) throws InterruptedException {
        long waitStart = logger.now();
        logger.resourceWaitStarted(job);

        resources.acquire(job.resource);   // อาจต้องรอ. ถ้าถูก interrupt ตรงนี้ = ยังไม่ได้บัตร จึงไม่ต้องคืน

        // ---- มาถึงบรรทัดนี้ = ถือบัตรอยู่แล้ว ต้องคืนให้ได้เสมอ ----
        try {
            job.resourceWaitMs = logger.now() - waitStart;
            logger.resourceAcquired(job, job.resourceWaitMs);
            Thread.sleep(job.resourceMs);  // ถือ resource (จำลองด้วย sleep)
        } finally {
            // finally ทำงานเสมอ แม้ถูก interrupt หรือเกิด exception -> บัตรไม่มีวันหาย
            resources.release(job.resource);
            logger.resourceReleased(job);
        }
    }
}
