import java.util.concurrent.Semaphore;

/**
 * ควบคุมสิทธิ์การใช้ทรัพยากรร่วม (PRINTER, DATABASE) ด้วย Semaphore
 *
 * Semaphore = ตัวนับ "บัตรผ่าน" (permit)
 *   acquire() : ขอบัตร 1 ใบ ถ้าบัตรหมดจะหลับรอจนมีคนคืน
 *   release() : คืนบัตร 1 ใบ
 *
 * new Semaphore(จำนวนบัตร, true)  <- true = fair: คนที่มารอก่อนได้ก่อน (First In First Out)
 *
 * Worker ทุกตัวใช้ ResourceManager ตัวเดียวกัน จึงแย่งบัตรชุดเดียวกัน
 */
public class ResourceManager {

    private final Semaphore printer;
    private final Semaphore database;
    private final int printerTotal;
    private final int databaseTotal;

    public ResourceManager(int printerPermits, int databasePermits) {
        this.printer = new Semaphore(printerPermits, true);
        this.database = new Semaphore(databasePermits, true);
        this.printerTotal = printerPermits;
        this.databaseTotal = databasePermits;
    }

    /** ขอสิทธิ์ใช้ทรัพยากร จะรอจนกว่าจะได้ (NONE = ไม่ต้องขอ) */
    public void acquire(ResourceType type) throws InterruptedException {
        Semaphore s = semaphoreOf(type);
        if (s != null) {
            s.acquire();
        }
    }

    /** คืนสิทธิ์ใช้ทรัพยากร */
    public void release(ResourceType type) {
        Semaphore s = semaphoreOf(type);
        if (s != null) {
            s.release();
        }
    }

    /** ข้อความสถานะ เช่น "printer=1/1 database=0/2"  (ใช้อยู่ / ทั้งหมด) */
    public String status() {
        return "printer=" + (printerTotal - printer.availablePermits()) + "/" + printerTotal
             + " database=" + (databaseTotal - database.availablePermits()) + "/" + databaseTotal;
    }

    /** เลือก Semaphore ตามชนิด (NONE ได้ null) */
    private Semaphore semaphoreOf(ResourceType type) {
        if (type == ResourceType.PRINTER) {
            return printer;
        }
        if (type == ResourceType.DATABASE) {
            return database;
        }
        return null;
    }
}
