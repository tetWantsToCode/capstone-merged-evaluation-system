import { useState } from "react";
import { useAppStore } from "@/store/useAppStore";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Plus, Calendar, CheckCircle, Clock } from "lucide-react";
import { toast } from "sonner";

const EvaluationManager = () => {
  const { activities, addActivity } = useAppStore();
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [deadline, setDeadline] = useState("");
  const [criteria, setCriteria] = useState(["Attendance", "Team Cooperation", "Respect", "Knowledge"]);
  const [newCriterion, setNewCriterion] = useState("");

  const handleCreate = () => {
    if (!title || !deadline) {
      toast.error("Please fill in all fields");
      return;
    }
    addActivity({
      id: `a${Date.now()}`,
      title,
      deadline,
      isActive: true,
      rubricCriteria: criteria.map((c, i) => ({ id: `c${i}`, name: c, description: c })),
      createdBy: "teacher1",
      createdAt: new Date().toISOString(),
    });
    toast.success("Evaluation session created! Students can now access and complete the evaluation form.");
    setOpen(false);
    setTitle("");
    setDeadline("");
  };

  const addCriterion = () => {
    if (newCriterion && !criteria.includes(newCriterion)) {
      setCriteria([...criteria, newCriterion]);
      setNewCriterion("");
    }
  };

  return (
    <Card className="shadow-card">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="text-xl font-bold">Create Evaluation Session</CardTitle>
            <CardDescription>Set session title, evaluation criteria (rubric), and deadline. Once published, the evaluation is available to students (SRS Module 3).</CardDescription>
          </div>
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button size="sm" className="gap-2 bg-primary text-primary-foreground">
                <Plus size={16} /> Create Evaluation
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-md">
              <DialogHeader>
                <DialogTitle>Create Evaluation Session</DialogTitle>
                <DialogDescription>Set up a new peer evaluation for your students</DialogDescription>
              </DialogHeader>
              <div className="space-y-4">
                <div>
                  <Label>Title</Label>
                  <Input placeholder="e.g., Sprint 2 Evaluation" value={title} onChange={(e) => setTitle(e.target.value)} />
                </div>
                <div>
                  <Label>Deadline</Label>
                  <Input type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
                </div>
                <div>
                  <Label>Rubric Criteria</Label>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {criteria.map((c) => (
                      <Badge key={c} variant="secondary" className="cursor-pointer" onClick={() => setCriteria(criteria.filter((x) => x !== c))}>
                        {c} ×
                      </Badge>
                    ))}
                  </div>
                  <div className="mt-2 flex gap-2">
                    <Input placeholder="Add criterion" value={newCriterion} onChange={(e) => setNewCriterion(e.target.value)} onKeyDown={(e) => e.key === "Enter" && addCriterion()} />
                    <Button variant="outline" size="sm" onClick={addCriterion}>Add</Button>
                  </div>
                </div>
                <Button className="w-full bg-primary text-primary-foreground" onClick={handleCreate}>
                  Publish Evaluation
                </Button>
              </div>
            </DialogContent>
          </Dialog>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {activities.map((activity) => (
            <div key={activity.id} className="flex items-center justify-between rounded-lg border border-border bg-card p-4 transition-all hover:shadow-card">
              <div className="flex-1">
                <h3 className="font-semibold text-card-foreground">{activity.title}</h3>
                <div className="mt-1 flex items-center gap-4 text-sm text-muted-foreground">
                  <span className="flex items-center gap-1">
                    <Calendar size={14} />
                    Due: {new Date(activity.deadline).toLocaleDateString()}
                  </span>
                  <span className="flex items-center gap-1">
                    {activity.rubricCriteria.length} criteria
                  </span>
                </div>
              </div>
              <Badge variant={activity.isActive ? "default" : "secondary"} className={activity.isActive ? "bg-primary text-primary-foreground" : ""}>
                {activity.isActive ? (
                  <><Clock size={12} className="mr-1" /> Active</>
                ) : (
                  <><CheckCircle size={12} className="mr-1" /> Closed</>
                )}
              </Badge>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
};

export default EvaluationManager;
