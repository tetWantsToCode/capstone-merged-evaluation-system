import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import ApeerLogo from "@/components/ApeerLogo";
import { CheckCircle2 } from "lucide-react";

const ThankYouPage = () => {
  const navigate = useNavigate();

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 text-center">
      <div className="animate-scale-in max-w-md">
        <div className="mb-6 flex justify-center">
          <div className="rounded-full bg-accent p-4">
            <CheckCircle2 size={48} className="text-primary" />
          </div>
        </div>
        <h1 className="mb-2 text-3xl font-extrabold text-foreground">
          THANK YOU
        </h1>
        <p className="mb-1 text-lg font-semibold text-foreground">
          for completing the peer evaluation.
        </p>
        <p className="mb-8 text-muted-foreground">
          Your feedback helps promote fairness and improvement within the group.
        </p>

        <Button
          onClick={() => navigate("/student")}
          className="gap-2 bg-primary text-primary-foreground"
        >
          Back to Dashboard
        </Button>

        <div className="mt-12">
          <ApeerLogo size="sm" />
        </div>
      </div>
    </div>
  );
};

export default ThankYouPage;
