import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import AppLayout from "@/components/AppLayout";
import { useAppStore } from "@/store/useAppStore";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { AlertCircle, ChevronLeft, ChevronRight, Send } from "lucide-react";
import { toast } from "sonner";

const EvaluationForm = () => {
  const { activityId } = useParams();
  const navigate = useNavigate();
  const { activities, students, currentUser, addSubmission } = useAppStore();

  const activity = activities.find((a) => a.id === activityId);
  const groupMembers = students.filter((s) => s.groupNumber === 1);
  const criteria = activity?.rubricCriteria || [];

  const [scores, setScores] = useState({});
  const [comment, setComment] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [showConfirm, setShowConfirm] = useState(false);
  const [errors, setErrors] = useState([]);

  const setScore = (criterionId, memberId, score) => {
    setScores((prev) => ({
      ...prev,
      [criterionId]: { ...(prev[criterionId] || {}), [memberId]: score },
    }));
  };

  const getScore = (criterionId, memberId) => {
    return scores[criterionId]?.[memberId] ?? null;
  };

  const validate = () => {
    const errs = [];

    for (const c of criteria) {
      for (const m of groupMembers) {
        if (getScore(c.id, m.id) === null) {
          errs.push(`Missing score for ${m.studentName} in ${c.name}`);
        }
      }
    }

    for (const c of criteria) {
      const usedScores = [];
      for (const m of groupMembers) {
        const s = getScore(c.id, m.id);
        if (s !== null) {
          if (usedScores.includes(s)) {
            errs.push(`Duplicate score ${s} in ${c.name}. Each member must have a unique score per criterion.`);
            break;
          }
          usedScores.push(s);
        }
      }
    }

    for (const c of criteria) {
      for (const m of groupMembers) {
        const s = getScore(c.id, m.id);
        if (s !== null && (s < 0 || s > 10)) {
          errs.push(`Score for ${m.studentName} in ${c.name} must be between 0 and 10`);
        }
      }
    }

    if (!comment.trim()) {
      errs.push("Please provide feedback comments");
    }

    return errs;
  };

  const handleNext = () => {
    const rubricsErrs = [];
    for (const c of criteria) {
      for (const m of groupMembers) {
        if (getScore(c.id, m.id) === null) {
          rubricsErrs.push(`Missing score for ${m.studentName} in ${c.name}`);
        }
      }
      const usedScores = [];
      for (const m of groupMembers) {
        const s = getScore(c.id, m.id);
        if (s !== null) {
          if (usedScores.includes(s)) {
            rubricsErrs.push(`Duplicate score ${s} in ${c.name}`);
            break;
          }
          usedScores.push(s);
        }
      }
    }

    if (rubricsErrs.length > 0) {
      setErrors(rubricsErrs);
      toast.error("Please fix the errors before continuing");
      return;
    }

    setErrors([]);
    setCurrentPage(1);
  };

  const handleSubmit = () => {
    const errs = validate();
    if (errs.length > 0) {
      setErrors(errs);
      toast.error("Please fix the errors before submitting");
      return;
    }
    setShowConfirm(true);
  };

  const confirmSubmit = () => {
    if (!currentUser || !activityId) return;

    const evalScores = criteria.flatMap((c) =>
      groupMembers.map((m) => ({
        memberId: m.id,
        criterionId: c.id,
        score: getScore(c.id, m.id) || 0,
      })),
    );

    addSubmission({
      id: `sub_${Date.now()}`,
      evaluatorId: currentUser.id,
      activityId,
      scores: evalScores,
      comment,
      submittedAt: new Date().toISOString(),
    });

    navigate("/thank-you");
  };

  if (!activity) {
    return (
      <AppLayout>
        <div className="py-16 text-center text-muted-foreground">Activity not found</div>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <div className="mb-4 flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => navigate("/student")} className="gap-1 text-muted-foreground">
          <ChevronLeft size={16} /> Back
        </Button>
      </div>

      <div className="mb-6">
        <h1 className="text-2xl font-extrabold text-foreground">{activity.title}</h1>
        <p className="text-sm text-muted-foreground">
          Rate each group member (0–10). Members are labeled by their order in the class list (Member 1, Member 2, etc.). Each member must receive exactly one unique score per criterion; duplicate scores within a criterion are not allowed.
        </p>
      </div>

      <div className="mb-6 flex gap-2">
        {["Rubric Scoring", "Feedback"].map((label, i) => (
          <div key={i} className="flex flex-1 items-center gap-2">
            <div
              className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold ${
                currentPage >= i ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
              }`}
            >
              {i + 1}
            </div>
            <span className={`text-sm font-medium ${currentPage >= i ? "text-foreground" : "text-muted-foreground"}`}>
              {label}
            </span>
            {i < 1 && <div className="h-px flex-1 bg-border" />}
          </div>
        ))}
      </div>

      {errors.length > 0 && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3">
          <div className="flex items-center gap-2 text-sm font-medium text-destructive">
            <AlertCircle size={16} /> Please fix the following errors:
          </div>
          <ul className="mt-1 list-inside list-disc text-sm text-destructive/80">
            {errors.slice(0, 3).map((e, i) => (
              <li key={i}>{e}</li>
            ))}
            {errors.length > 3 && <li>...and {errors.length - 3} more</li>}
          </ul>
        </div>
      )}

      {currentPage === 0 && (
        <div className="space-y-6">
          <Card className="overflow-hidden shadow-card">
            <CardContent className="p-0">
              <div
                className="grid border-b bg-muted/50 p-3"
                style={{ gridTemplateColumns: `200px repeat(${groupMembers.length}, 1fr)` }}
              >
                <div className="text-sm font-semibold text-muted-foreground">Criteria</div>
                {groupMembers.map((m, idx) => (
                  <div key={m.id} className="text-center">
                    <div className="text-xs font-medium text-muted-foreground">Member {idx + 1}</div>
                    <div className="text-sm font-semibold text-card-foreground">{m.studentName.split(" ")[0]}</div>
                    {m.id === currentUser?.id && (
                      <Badge variant="secondary" className="mt-0.5 text-[10px]">
                        You
                      </Badge>
                    )}
                  </div>
                ))}
              </div>

              {criteria.map((criterion) => (
                <div
                  key={criterion.id}
                  className="grid items-center border-b p-3 last:border-b-0"
                  style={{ gridTemplateColumns: `200px repeat(${groupMembers.length}, 1fr)` }}
                >
                  <div>
                    <div className="text-sm font-semibold text-card-foreground">{criterion.name}</div>
                    <div className="text-xs text-muted-foreground">{criterion.description}</div>
                  </div>
                  {groupMembers.map((member) => {
                    const score = getScore(criterion.id, member.id);
                    const allScoresInCriterion = groupMembers
                      .filter((m) => m.id !== member.id)
                      .map((m) => getScore(criterion.id, m.id))
                      .filter((s) => s !== null);
                    const isDuplicate = score !== null && allScoresInCriterion.includes(score);

                    return (
                      <div key={member.id} className="flex justify-center">
                        <div className="flex flex-wrap justify-center gap-1">
                          {Array.from({ length: 11 }, (_, i) => (
                            <button
                              key={i}
                              onClick={() => setScore(criterion.id, member.id, i)}
                              className={`flex h-7 w-7 items-center justify-center rounded text-xs font-medium transition-all ${
                                score === i
                                  ? isDuplicate
                                    ? "bg-destructive text-destructive-foreground"
                                    : "bg-primary text-primary-foreground shadow-sm"
                                  : "bg-muted text-muted-foreground hover:bg-accent"
                              }`}
                            >
                              {i}
                            </button>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ))}
            </CardContent>
          </Card>

          <div className="flex justify-end">
            <Button onClick={handleNext} className="gap-2 bg-primary text-primary-foreground">
              Next <ChevronRight size={16} />
            </Button>
          </div>
        </div>
      )}

      {currentPage === 1 && (
        <div className="space-y-6">
          <Card className="shadow-card">
            <CardHeader>
              <CardTitle>Other Feedback</CardTitle>
            </CardHeader>
            <CardContent>
              <Label className="text-sm text-muted-foreground">
                Share your additional thoughts or constructive feedback. Your feedback will remain confidential and will
                only be visible to the teacher.
              </Label>
              <Textarea
                placeholder="Write your feedback here..."
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                className="mt-3 min-h-[150px]"
              />
            </CardContent>
          </Card>

          <div className="flex justify-between">
            <Button variant="outline" onClick={() => setCurrentPage(0)} className="gap-2">
              <ChevronLeft size={16} /> Back
            </Button>
            <Button onClick={handleSubmit} className="gap-2 bg-primary text-primary-foreground">
              <Send size={16} /> Submit Evaluation
            </Button>
          </div>
        </div>
      )}

      <Dialog open={showConfirm} onOpenChange={setShowConfirm}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm Submission</DialogTitle>
            <DialogDescription>
              You have unsaved changes. Are you sure you want to submit? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <div className="flex gap-2 justify-end">
            <Button variant="outline" onClick={() => setShowConfirm(false)}>
              Cancel
            </Button>
            <Button className="bg-primary text-primary-foreground" onClick={confirmSubmit}>
              Submit
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </AppLayout>
  );
};

export default EvaluationForm;
