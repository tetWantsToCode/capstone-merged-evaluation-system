import { useRef } from "react";
import { useAppStore } from "@/store/useAppStore";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Upload, Download } from "lucide-react";
import { toast } from "sonner";
import * as XLSX from "xlsx";

const StudentManagement = () => {
  const { students, setStudents } = useAppStore();
  const fileInputRef = useRef(null);

  const handleUpload = (e) => {
    const file = e.target?.files?.[0];
    if (!file) return;
    if (!file.name.endsWith(".xlsx") && !file.name.endsWith(".xls")) {
      toast.error("Please upload an Excel file (.xlsx or .xls).");
      return;
    }
    const reader = new FileReader();
    reader.onload = (ev) => {
      try {
        const data = new Uint8Array(ev.target.result);
        const workbook = XLSX.read(data, { type: "array" });
        const firstSheet = workbook.Sheets[workbook.SheetNames[0]];
        const rows = XLSX.utils.sheet_to_json(firstSheet, { header: 1 });
        if (!rows.length) {
          toast.error("File is empty or has no data.");
          return;
        }
        const headers = rows[0].map((h) => String(h || "").toLowerCase());
        const nameCol = headers.findIndex((h) => h.includes("name") || h === "student name");
        const gmailCol = headers.findIndex((h) => h.includes("gmail") || h.includes("email"));
        const groupCol = headers.findIndex((h) => h.includes("group"));
        if (nameCol === -1 || gmailCol === -1) {
          toast.error("Excel must have columns for student name and Gmail (or email).");
          return;
        }
        const list = [];
        for (let i = 1; i < rows.length; i++) {
          const row = rows[i];
          const studentName = row[nameCol] != null ? String(row[nameCol]).trim() : "";
          const gmail = row[gmailCol] != null ? String(row[gmailCol]).trim() : "";
          if (!studentName && !gmail) continue;
          if (!gmail) {
            toast.error(`Row ${i + 2}: Gmail is required.`);
            return;
          }
          list.push({
            id: `s_${Date.now()}_${i}`,
            studentName: studentName || "Unknown",
            gmail,
            groupNumber: groupCol >= 0 && row[groupCol] != null ? Number(row[groupCol]) || 1 : 1,
          });
        }
        setStudents(list);
        toast.success(`Imported ${list.length} student(s). Student list updated.`);
      } catch (err) {
        toast.error("Failed to read Excel file. Check the format.");
        console.error(err);
      }
    };
    reader.readAsArrayBuffer(file);
    e.target.value = "";
  };

  const handleExport = () => {
    const headers = ["#", "Student Name", "Gmail Account", "Group"];
    const rows = students.map((s, i) => [i + 1, s.studentName, s.gmail, s.groupNumber ?? ""]);
    const ws = XLSX.utils.aoa_to_sheet([headers, ...rows]);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Student List");
    XLSX.writeFile(wb, "apeer_student_list.xlsx");
    toast.success("Student list exported as apeer_student_list.xlsx");
  };

  return (
    <Card className="shadow-card">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="text-xl font-bold">Student List (Teacher&apos;s Class Record)</CardTitle>
            <CardDescription>
              Upload an Excel (.xlsx) file with student names and Gmail accounts. The system validates the format, extracts data, and stores the list. You can export the current list back to Excel.
            </CardDescription>
          </div>
          <div className="flex gap-2">
            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx,.xls"
              className="hidden"
              onChange={handleUpload}
            />
            <Button variant="outline" size="sm" className="gap-2" onClick={handleExport}>
              <Download size={16} /> Export
            </Button>
            <Button
              size="sm"
              className="gap-2 bg-primary text-primary-foreground"
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload size={16} /> Upload Excel
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/50">
                <TableHead className="w-16">#</TableHead>
                <TableHead>Student Name</TableHead>
                <TableHead>Gmail Account</TableHead>
                <TableHead className="w-24">Group</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {students.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                    No students yet. Upload an Excel file to add your class roster.
                  </TableCell>
                </TableRow>
              ) : (
                students.map((student, idx) => (
                  <TableRow key={student.id}>
                    <TableCell className="font-medium text-muted-foreground">{idx + 1}</TableCell>
                    <TableCell className="font-medium text-card-foreground">{student.studentName}</TableCell>
                    <TableCell className="text-muted-foreground">{student.gmail}</TableCell>
                    <TableCell>
                      <span className="rounded-full bg-accent px-2.5 py-0.5 text-xs font-semibold text-accent-foreground">
                        Group {student.groupNumber}
                      </span>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  );
};

export default StudentManagement;
