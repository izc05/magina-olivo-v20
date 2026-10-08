type OliveDecorProps = {
  className?: string;
  flip?: boolean;
  dark?: boolean;
};

export function OliveDecor({
  className = "",
  flip = false,
  dark = false,
}: OliveDecorProps) {
  const leaf = dark ? "#2f4937" : "#66764f";
  const leafSoft = dark ? "#53673e" : "#8f9a75";
  const stem = dark ? "#53673e" : "#9d9270";

  return (
    <svg
      className={`olive-decor ${flip ? "olive-decor-flip" : ""} ${className}`}
      viewBox="0 0 260 220"
      aria-hidden="true"
      focusable="false"
    >
      <path
        d="M18 198C76 151 120 112 160 70c24-25 49-41 82-55"
        fill="none"
        stroke={stem}
        strokeWidth="4"
        strokeLinecap="round"
      />
      <path d="M72 155c-45 5-63-13-66-39 31-7 56 5 66 39Z" fill={leafSoft} />
      <path d="M98 132c-9-40 6-63 32-72 14 28 7 56-32 72Z" fill={leaf} />
      <path d="M122 109c-43 0-62-19-61-45 31-3 55 12 61 45Z" fill={leafSoft} />
      <path d="M151 80c-5-37 12-58 37-65 11 28 1 53-37 65Z" fill={leaf} />
      <path d="M171 61c-35-7-48-29-41-51 28 2 47 20 41 51Z" fill={leafSoft} />
      <path d="M201 40c7-26 26-38 47-34-1 24-18 39-47 34Z" fill={leaf} />
      <ellipse cx="133" cy="117" rx="13" ry="20" fill="#58663c" transform="rotate(24 133 117)" />
      <ellipse cx="156" cy="91" rx="12" ry="18" fill="#405432" transform="rotate(21 156 91)" />
    </svg>
  );
}
