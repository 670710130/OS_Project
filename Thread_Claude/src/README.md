# Mini Job Scheduler & Shared Resource Manager
517-312 Operating Systems — Term Project

## วิธี Compile

```bash
javac *.java
```

คอมไพล์ไฟล์ `.java` ทั้งหมดในโฟลเดอร์เดียวกัน 

## วิธี Run

```bash
java Main <workload.csv> <fcfs|priority> <workers> <printerPermits> <databasePermits>
```

ตัวอย่าง:
```bash
java Main jobs_standard.csv priority 3 1 2
```

บันทึก log ลงไฟล์ (redirect ตอนรัน ไม่ต้องเขียนโค้ดเพิ่ม):
```bash
java Main jobs_standard.csv priority 3 1 2 > ../logs/standard_priority_w3.log
```

## ค่าที่ใส่ตอนรันโปรแกรม (Command-Line Arguments)

ต้องใส่ให้ครบทั้ง 5 ตัวตามลำดับ ใส่ไม่ครบหรือค่าผิดโปรแกรมจะแสดง Usage แล้วจบทันที

| # | argument | ความหมาย | ตัวอย่าง |
|---|---|---|---|
| 1 | `workload.csv` | path ของไฟล์ชุดงานทดสอบ | `jobs_standard.csv` |
| 2 | `fcfs \| priority` | นโยบายการจัดลำดับงาน | `priority` |
| 3 | `workers` | จำนวน Worker Thread (≥ 1) | `3` |
| 4 | `printerPermits` | จำนวนสิทธิ์ใช้ PRINTER พร้อมกัน (≥ 1) | `1` |
| 5 | `databasePermits` | จำนวนสิทธิ์ใช้ DATABASE พร้อมกัน (≥ 1) | `2` |

ไม่มีค่า default ซ่อนอยู่เลย ทุกครั้งที่รันต้องระบุครบทั้ง 5 ตัว

## ข้อจำกัดที่ควรรู้ 

- รองรับเฉพาะ **FCFS** และ **Priority** (non-preemptive ระดับ Job) — ไม่ได้ทำ Aging/MLFQ
- Job หนึ่งตัวใช้ shared resource ได้ไม่เกิน 1 ชนิด  จึงไม่มีกลไกป้องกัน deadlock จากการถือหลาย resource พร้อมกัน
- Semaphore ของ PRINTER/DATABASE สร้างแบบ **fair = true** เสมอ
- tie-break เมื่อ priority เท่ากัน ตัดสินตามลำดับ: priority → arrivalMs → sequence (ลำดับในไฟล์ CSV) เป็นกติกาที่กลุ่มออกแบบ
ำ
- ผลของแต่ละรอบรัน (timestamp, ลำดับเหตุการณ์บางบรรทัดใน log) อาจ**ต่างกันเล็กน้อย**แม้ใช้ workload และค่าที่รันเหมือนกันทุกตัว เพราะ `Thread.sleep()` ไม่แม่นยำทุกครั้ง และ OS สลับคิว Thread ไม่เหมือนเดิมทุกครั้ง
- แม้จะใช้ `chcp 65001` แต่จะได้ผลเฉพาะเมื่อพิมพ์ usage error เท่านั้น ภายใน log ที่บันทึกผลไม่สามารถทำให้เป็นภาษาไทยได้
