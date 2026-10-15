# หัวข้อ 14: รูปแบบผลลัพธ์ 1 หน้า


## ตารางสรุปผล



| # | Workload / Policy | Workers | Printer | DB | avg WT (ms) | avg TAT (ms) | Throughput (jobs/s) | avg RW (ms) | คำสั่งที่ใช้รัน |
|---|---|---|---|---|---|---|---|---|---|
| 1 | standard / FCFS | 3 | 1 | 2 |2964 |5471 |1.14 |77 | `java Main jobs_standard.csv fcfs 3 1 2` |
| 2 | standard / Priority | 3 | 1 | 2 | 2333 | 4966 |1.08 |255 | `java Main jobs_standard.csv priority 3 1 2` |
| 3 | standard / Priority | 1 | 1 | 2 | 8558 |11013 |0.41 |1 | `java Main jobs_standard.csv priority 1 1 2` |
| 4 | standard / Priority | 5 | 1 | 2 |1135 |4087 |1.29 |720 | `java Main jobs_standard.csv priority 5 1 2` |
| 5 | printer / Priority | 3 | 1 | 2 |1158 |4265 |0.80 |1619 | `java Main jobs_printer.csv priority 3 1 2` |
| 6 | printer / Priority | 3 | 2 | 2 |771 |2562 |1.42 |306 | `java Main jobs_printer.csv priority 3 2 2` |
| 7 | standard / Priority (repeat) | 3 | 1 | 2 |2329 |4958 |1.08 |255 | `java Main jobs_standard.csv priority 3 1 2` |



---

## คำถาม

### 1. เพิ่ม Worker จาก 3 เป็น 5 แล้ว Throughput เพิ่มตามสัดส่วนหรือไม่ และ avg Resource Wait เปลี่ยนอย่างไร เพราะอะไร?

>Throughput เพิ่มขึ้นแต่ **ไม่ค่อยเป็นสัดส่วน** กับจำนวน Worker ที่เพิ่ม จาก  1.08 เป็น 1.29 jobs/s เพราะระบบมีคอขวดอยู่ที่ shared resource (PRINTER permit=1) ซึ่งไม่ได้เพิ่มตามจำนวน Worker — เมื่อ Worker มากขึ้น งานหลายตัวทำ `workMs` เสร็จใกล้เวลากันมากขึ้น ทำให้แย่งขอ resource ตัวเดียวกันพร้อมกันบ่อยขึ้น avg Resource Wait จึงเพิ่มขึ้น จาก 255 ms เป็น 725 ms นี่คือจุดที่เพิ่ม Worker ต่อแล้วได้ประโยชน์น้อยลง เพราะ Worker ที่เพิ่มมาต้องมาต่อคิวรอ resource แทนที่จะได้ทำงานขนานจริง

### 2. FCFS กับ Priority ทำให้ Job กลุ่มใดได้ประโยชน์หรือเสียประโยชน์จาก jobs_standard.csv? อ้างอิง Waiting Time และลำดับ START ใน log พร้อมอธิบายว่าทำไม Priority อาจมี Throughput ต่ำกว่า FCFS แม้ avg Waiting Time จะดีกว่า


>งานที่มี priority สูง (priority=1 คือ J07, J08, J09) ได้ประโยชน์ชัดเจนภายใต้ Priority Scheduling
Waiting Time ลดลงจาก FCFS อย่างมาก:
  J07: 4963 ms (FCFS) → 1550 ms (Priority)
  J08: 5230 ms (FCFS) → 1502 ms (Priority)
  J09: 5716 ms (FCFS) → 2324 ms (Priority)
>เพราะถูกเลือกก่อนแม้จะมาถึงทีหลังงาน priority=4/5 ที่เข้าคิวรออยู่แล้ว ดูลำดับ JOB_STARTED:
ในส่วนของ `FCFS worker-1` เริ่ม J01→J04→J07→J10 ตามลำดับเวลาที่เข้าคิว
แต่ในส่วน `Priority worker-1` เริ่ม J01→J07→J10→J06 คือ J07 (priority=1) แทรกหน้า J04/J05/J06
(priority=4-5) ทั้งที่มาถึงทีหลัง

>ส่วนงาน priority ต่ำ (priority=4-5 คือ J01-J06) เสียประโยชน์ เพราะต้องรอให้งาน priority
สูงกว่าหมดคิวก่อน ตัวอย่าง:
  J04: WT 2231 ms (FCFS) → 5039 ms (Priority)
  J05: WT 2313 ms (FCFS) → 5144 ms (Priority)
  J06: WT 3212 ms (FCFS) → 4743 ms (Priority)

>เหตุผลที่ Priority มี Throughput ต่ำกว่า FCFS (1.08 เทียบ 1.14 jobs/s) ทั้งที่ avg WT ดีกว่า
(2333 เทียบ 2964 ms) คือ avg Resource Wait สูงกว่ามาก (255 ms เทียบ 77 ms) เพราะ Priority
Scheduling ทำให้งาน priority เท่ากันถูกปล่อยออกมาทำงานพร้อมกันเป็นกลุ่ม (bunching) ซึ่งมักต้องขอ
shared resource ตัวเดียวกันในช่วงเวลาใกล้กันด้วย จาก log เห็นได้ชัดที่ J07 กับ J08 มาขอ PRINTER
เกือบพร้อมกัน (3254ms กับ 3316ms) ทำให้ J08 ต้องรอถึง 1316ms กว่าจะได้ printer ในขณะที่ภายใต้
FCFS การมาขอ resource กระจายตัวตามเวลาที่แต่ละงานเข้าคิวตามธรรมชาติ ไม่กระจุกตัว จึงมีงานที่ต้อง
รอนาน (>500ms) เพียงตัวเดียว (J08 รอ 535ms) ส่งผลให้ระบบโดยรวมประมวลผลงานได้ช้าลงแม้แต่ละงานจะ
เริ่มทำเร็วขึ้นก็ตาม

### 3. เมื่อเพิ่ม Printer permit จาก 1 เป็น 2 แล้ว avg Resource Wait และ Throughput เปลี่ยนอย่างไร และจุดคอขวดของระบบย้ายไปอยู่ที่ใด?

> avg Resource Wait ลดลงอย่างชัดเจน จาก 1618 ms เหลือ 306 ms เพราะมี permit ให้ใช้พร้อมกันได้ 2 ใบ
งานไม่ต้องผลัดกันรอคิว PRINTER นานเท่าเดิม Throughput เพิ่มขึ้นจาก 0.80 เป็น 1.42 jobs/s และ makespan
สั้นลงจาก 7523 ms เหลือ 4234 ms

> อย่างไรก็ตาม PRINTER ยังไม่หมดบทบาทการเป็นคอขวดไปทั้งหมด เพราะ workers=3 มากกว่า printer permit=2 อยู่ 1 หน่วย
จาก log MONITOR ของรอบ printer=2 พบว่า printer=2/2 (เต็ม) อยู่ถึง 3 จาก 4 ครั้งที่ตรวจ (ที่ 1049ms, 2057ms, 3069ms)
แสดงว่าเมื่อ Worker ทั้ง 3 ตัวทำงานหลักเสร็จใกล้เคียงกัน ก็ยังมีอย่างน้อย 1 ตัวต้องรอ PRINTER เสมอ ซึ่งส่วนนี้ Data Base จะไม่มีการใช้ทรัพยากรใดๆ

>สรุปคือคอขวดเปลี่ยนจาก "PRINTER ที่เป็นจุดคอขวดรุนแรง" (permit=1 < workers=3 มาก) เป็น "PRINTER ที่เป็นขอขวดเล็กน้อย" (permit=2 ยังน้อยกว่า workers=3 อยู่ 1) ถ้าเพิ่ม permit เป็น 3 เท่ากับจำนวน Worker น่าจะทำให้
avg Resource Wait เข้าใกล้ 0 และคอขวดจะย้ายไปอยู่ที่จำนวน Worker = 3  เพราะจะกลายเป็นขีดจำกัด
การทำงานขนานสูงสุดของระบบแทน

### 4. เปรียบเทียบแถว 2 กับแถว 7: เหตุใด workload และชุดค่าที่ใช้รันเหมือนเดิม จึงอาจให้เวลาใน log ลำดับเหตุการณ์ และค่าที่วัดต่างกันเล็กน้อย?

>
> แม้ใช้ workload และค่าที่รันเหมือนกันทุกตัว ผลแต่ละรอบต่างกันเล็กน้อยเพราะ ระบบปฏิบัติการ: (1) `Thread.sleep(ms)` ไม่รับประกันว่าจะตื่นตรงตามเวลาที่ขอ มักตื่นช้ากว่าเล็กน้อยเสมอ ไม่เท่ากันทุกครั้ง และ (2) OS Scheduler เลือกสลับให้ Thread ไหนได้ครอบครอง CPU ก่อน-หลังไม่เหมือนเดิมทุกรอบ แม้ Job และเงื่อนไขการตัดสินใจ (tie-break) จะเหมือนเดิมทุกอย่าง ผลคือ timestamp ในแต่ละบรรทัดของ log ขยับไปเล็กน้อย เช่น job07 เริ่มที่ 2550 ms ในรอบแรก แต่มีค่า 2552 ms ในรอบสอง ส่งผลให้ค่าเฉลี่ยที่คำนวณได้ (avg WT, avg TAT, avg RW, Throughput) ต่างกันเล็กน้อย
