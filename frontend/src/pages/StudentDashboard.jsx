import { useNavigate } from "react-router-dom";
import AppLayout from "@/components/AppLayout";
import { useAppStore } from "@/store/useAppStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ClipboardCheck, Calendar, ArrowRight, CheckCircle2 } from "lucide-react";

const StudentDashboard = () => {
  const { activities, currentUser, hasSubmitted } = useAppStore();
  const navigate = useNavigate();

  return (
    <AppLayout>
      <div className="mb-6">
        <h1 className="text-3xl font-extrabold text-foreground">Student Dashboard</h1>
        <p className="mt-1 text-muted-foreground">
          View and complete your peer evaluations
        </p>
      </div>

      <div className="space-y-4">
        {activities.filter((a) => a.isActive).map((activity) => {
          const submitted = currentUser ? hasSubmitted(activity.id, currentUser.id) : false;

          return (
            <Card key={activity.id} className="shadow-card transition-all hover:shadow-elevated">
              <CardContent className="flex items-center justify-between p-6">
                <div className="flex items-start gap-4">
                  <div className={`rounded-lg p-3 ${submitted ? "bg-accent" : "bg-secondary"}`}>
                    <ClipboardCheck size={24} className={submitted ? "text-primary" : "text-secondary-foreground"} />
                  </div>
                  <div>
                    <h3 className="text-lg font-bold text-card-foreground">{activity.title}</h3>
                    <div className="mt-1 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
                      <span className="flex items-center gap-1">
                        <Calendar size={14} />
                        Due: {new Date(activity.deadline).toLocaleDateString()}
                      </span>
                      <span>{activity.rubricCriteria.length} criteria</span>
                    </div>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {activity.rubricCriteria.map((c) => (
                        <Badge key={c.id} variant="secondary" className="text-xs">
                          {c.name}
                        </Badge>
                      ))}
                    </div>
                  </div>
                </div>

                {submitted ? (
                  <Badge className="gap-1.5 bg-accent text-accent-foreground">
                    <CheckCircle2 size={14} /> Submitted
                  </Badge>
                ) : (
                  <Button
                    onClick={() => navigate(`/evaluate/${activity.id}`)}
                    className="gap-2 bg-primary text-primary-foreground"
                  >
                    Start Evaluation <ArrowRight size={16} />
                  </Button>
                )}
              </CardContent>
            </Card>
          );
        })}

        {activities.filter((a) => a.isActive).length === 0 && (
          <div className="py-16 text-center text-muted-foreground">
            No active evaluations at the moment.
          </div>
        )}
      </div>
    </AppLayout>
  );
};

export default StudentDashboard;
