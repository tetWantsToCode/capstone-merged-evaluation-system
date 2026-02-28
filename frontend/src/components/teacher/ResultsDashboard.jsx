import { useAppStore } from "@/store/useAppStore";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Brain, TrendingUp, TrendingDown, Lightbulb, Star } from "lucide-react";

const ResultsDashboard = () => {
  const { finalScores, summaries } = useAppStore();

  return (
    <div className="space-y-6">
      <Card className="shadow-card">
        <CardHeader>
          <CardTitle className="text-xl font-bold">Final Scores</CardTitle>
          <CardDescription>
            Adjusted average: highest and lowest scores removed, then average of the rest. If total scores ≤ 2, normal average is used (SRS Module 5).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow className="bg-muted/50">
                  <TableHead>Student</TableHead>
                  <TableHead className="text-center">Adjusted Average</TableHead>
                  <TableHead className="text-center">Evaluators</TableHead>
                  <TableHead className="text-center">Raw Scores</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {finalScores.map((score) => (
                  <TableRow key={score.studentId}>
                    <TableCell className="font-medium text-card-foreground">{score.studentName}</TableCell>
                    <TableCell className="text-center">
                      <span className={`inline-flex items-center gap-1 rounded-full px-3 py-1 text-sm font-bold ${
                        score.adjustedAverage >= 8 ? "bg-accent text-accent-foreground" :
                        score.adjustedAverage >= 6 ? "bg-secondary text-secondary-foreground" :
                        "bg-destructive/10 text-destructive"
                      }`}>
                        <Star size={14} />
                        {score.adjustedAverage.toFixed(1)}
                      </span>
                    </TableCell>
                    <TableCell className="text-center text-muted-foreground">{score.totalEvaluators}</TableCell>
                    <TableCell className="text-center">
                      <div className="flex justify-center gap-1">
                        {score.rawScores.map((rs, i) => (
                          <span key={i} className="rounded bg-muted px-1.5 py-0.5 text-xs font-medium text-muted-foreground">
                            {rs}
                          </span>
                        ))}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      <Card className="shadow-card">
        <CardHeader>
          <div className="flex items-center gap-2">
            <div className="rounded-lg bg-accent p-2">
              <Brain size={20} className="text-primary" />
            </div>
            <div>
              <CardTitle className="text-xl font-bold">AI-Generated Insights</CardTitle>
              <CardDescription>Comment collection and AI summary per student (SRS Module 4). Strengths, weaknesses, areas for improvement.</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {summaries.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">
              AI summaries will appear here once evaluations are submitted.
            </p>
          ) : (
            <div className="space-y-4">
              {summaries.map((summary) => (
                <div key={summary.studentId} className="rounded-xl border border-border bg-card p-5">
                  <h3 className="mb-3 text-lg font-bold text-card-foreground">{summary.studentName}</h3>
                  <div className="grid gap-3 md:grid-cols-3">
                    <div className="rounded-lg bg-accent/50 p-3">
                      <div className="mb-1.5 flex items-center gap-1.5 text-sm font-semibold text-primary">
                        <TrendingUp size={14} /> Strengths
                      </div>
                      <p className="text-sm text-muted-foreground">{summary.strengths}</p>
                    </div>
                    <div className="rounded-lg bg-secondary/30 p-3">
                      <div className="mb-1.5 flex items-center gap-1.5 text-sm font-semibold text-apeer-warning">
                        <TrendingDown size={14} /> Weaknesses
                      </div>
                      <p className="text-sm text-muted-foreground">{summary.weaknesses}</p>
                    </div>
                    <div className="rounded-lg bg-muted p-3">
                      <div className="mb-1.5 flex items-center gap-1.5 text-sm font-semibold text-apeer-info">
                        <Lightbulb size={14} /> Areas for Improvement
                      </div>
                      <p className="text-sm text-muted-foreground">{summary.areasForImprovement}</p>
                    </div>
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground">
                    Generated: {new Date(summary.generatedAt).toLocaleString()}
                  </p>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default ResultsDashboard;
