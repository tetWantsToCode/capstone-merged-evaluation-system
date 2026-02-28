import AppLayout from "@/components/AppLayout";
import { useAppStore } from "@/store/useAppStore";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import StudentManagement from "@/components/teacher/StudentManagement";
import EvaluationManager from "@/components/teacher/EvaluationManager";
import ResultsDashboard from "@/components/teacher/ResultsDashboard";
import { Users, ClipboardList, BarChart3 } from "lucide-react";

const TeacherDashboard = () => {
  const { students, activities, submissions } = useAppStore();

  return (
    <AppLayout>
      <div className="mb-6">
        <h1 className="text-3xl font-extrabold text-foreground">Teacher Dashboard</h1>
        <p className="mt-1 text-muted-foreground">
          Manage students, evaluations, and view AI-powered results
        </p>
      </div>

      <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
        {[
          { label: "Students", value: students.length, icon: Users, color: "text-primary" },
          { label: "Active Evaluations", value: activities.filter((a) => a.isActive).length, icon: ClipboardList, color: "text-apeer-warning" },
          { label: "Submissions", value: submissions.length, icon: BarChart3, color: "text-apeer-info" },
        ].map((stat, i) => (
          <div key={i} className="flex items-center gap-4 rounded-xl border border-border bg-card p-5 shadow-card">
            <div className="rounded-lg bg-accent p-2.5">
              <stat.icon size={22} className={stat.color} />
            </div>
            <div>
              <p className="text-2xl font-bold text-card-foreground">{stat.value}</p>
              <p className="text-sm text-muted-foreground">{stat.label}</p>
            </div>
          </div>
        ))}
      </div>

      <Tabs defaultValue="students" className="space-y-4">
        <TabsList className="bg-muted">
          <TabsTrigger value="students" className="gap-2">
            <Users size={16} /> Students
          </TabsTrigger>
          <TabsTrigger value="evaluations" className="gap-2">
            <ClipboardList size={16} /> Evaluations
          </TabsTrigger>
          <TabsTrigger value="results" className="gap-2">
            <BarChart3 size={16} /> Results
          </TabsTrigger>
        </TabsList>

        <TabsContent value="students">
          <StudentManagement />
        </TabsContent>
        <TabsContent value="evaluations">
          <EvaluationManager />
        </TabsContent>
        <TabsContent value="results">
          <ResultsDashboard />
        </TabsContent>
      </Tabs>
    </AppLayout>
  );
};

export default TeacherDashboard;
