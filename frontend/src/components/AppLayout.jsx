import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import ApeerLogo from "@/components/ApeerLogo";
import { useAppStore } from "@/store/useAppStore";
import { LogOut, User } from "lucide-react";

const AppLayout = ({ children }) => {
  const { currentUser, logout } = useAppStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/");
  };

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <header className="sticky top-0 z-50 border-b border-border bg-card/80 backdrop-blur-sm">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3 md:px-8">
          <ApeerLogo size="sm" />
          <div className="flex items-center gap-3">
            {currentUser && (
              <div className="flex items-center gap-2 rounded-full bg-accent px-3 py-1.5">
                <User size={14} className="text-accent-foreground" />
                <span className="text-sm font-medium text-accent-foreground">
                  {currentUser.name}
                </span>
                <span className="rounded-full bg-primary px-2 py-0.5 text-xs font-semibold capitalize text-primary-foreground">
                  {currentUser.role}
                </span>
              </div>
            )}
            <Button variant="ghost" size="sm" onClick={handleLogout} className="gap-1.5 text-muted-foreground">
              <LogOut size={16} />
              Sign Out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 md:px-8">
        {children}
      </main>
    </div>
  );
};

export default AppLayout;
