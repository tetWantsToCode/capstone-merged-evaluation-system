import { Leaf } from "lucide-react";

const ApeerLogo = ({ size = "md", showText = true }) => {
  const iconSize = size === "sm" ? 20 : size === "md" ? 28 : 40;
  const textClass = size === "sm" ? "text-lg" : size === "md" ? "text-2xl" : "text-4xl";

  return (
    <div className="flex items-center gap-2">
      <div className="gradient-primary rounded-lg p-1.5 shadow-glow">
        <Leaf className="text-primary-foreground" size={iconSize} />
      </div>
      {showText && (
        <span className={`${textClass} font-extrabold tracking-tight text-foreground`}>
          A<span className="text-primary">PEER</span>
        </span>
      )}
    </div>
  );
};

export default ApeerLogo;
