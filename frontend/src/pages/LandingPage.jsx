import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import ApeerLogo from "@/components/ApeerLogo";
import { Users, ClipboardCheck, Brain, ArrowRight } from "lucide-react";

const LandingPage = () => {
  const navigate = useNavigate();

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <header className="flex items-center justify-between px-6 py-4 md:px-12">
        <ApeerLogo />
        <Button onClick={() => navigate("/login")} variant="outline" size="sm">
          Sign In
        </Button>
      </header>

      <main className="flex flex-1 flex-col items-center justify-center px-6 text-center">
        <div className="animate-fade-in max-w-2xl">
          <div className="mb-6 inline-flex items-center gap-2 rounded-full bg-accent px-4 py-1.5 text-sm font-medium text-accent-foreground">
            <Brain size={16} />
            AI-Powered Evaluation
          </div>

          <h1 className="mb-4 text-4xl font-extrabold leading-tight tracking-tight text-foreground md:text-6xl">
            Welcome to <span className="text-primary">APEER</span>
            <br />
            <span className="text-3xl font-bold text-muted-foreground md:text-4xl">
              AI Self and Peer Evaluation System
            </span>
          </h1>

          <p className="mx-auto mb-8 max-w-lg text-lg text-muted-foreground">
            A secure web application with Google-based login, Excel student lists, peer and self-evaluation, AI-generated summaries, and adjusted average scoring for fairness.
          </p>

          <Button
            onClick={() => navigate("/login")}
            size="lg"
            className="gradient-primary gap-2 px-8 text-lg font-semibold text-primary-foreground shadow-glow transition-all hover:scale-105"
          >
            Start Evaluate
            <ArrowRight size={20} />
          </Button>
        </div>

        <div className="mt-16 grid max-w-4xl grid-cols-1 gap-6 md:grid-cols-3" style={{ animationDelay: "0.2s" }}>
          {[
            {
              icon: Users,
              title: "Peer Evaluation",
              desc: "Evaluate group members with structured rubric criteria",
            },
            {
              icon: ClipboardCheck,
              title: "Fair Scoring",
              desc: "Adjusted averages remove outlier scores for fairness",
            },
            {
              icon: Brain,
              title: "AI Insights",
              desc: "AI-generated summaries of strengths and improvements",
            },
          ].map((f, i) => (
            <div
              key={i}
              className="animate-fade-in rounded-xl border border-border bg-card p-6 shadow-card transition-all hover:shadow-elevated"
              style={{ animationDelay: `${0.3 + i * 0.1}s` }}
            >
              <div className="mb-3 inline-flex rounded-lg bg-accent p-2.5">
                <f.icon size={22} className="text-primary" />
              </div>
              <h3 className="mb-1 text-lg font-bold text-card-foreground">{f.title}</h3>
              <p className="text-sm text-muted-foreground">{f.desc}</p>
            </div>
          ))}
        </div>
      </main>

      <footer className="py-6 text-center text-sm text-muted-foreground">
        © 2026 APEER — Cebu Institute of Technology University
      </footer>
    </div>
  );
};

export default LandingPage;
