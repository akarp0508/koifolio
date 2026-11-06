/** The koi from koibranding, with no background. Lime can't be seen on paper, so it takes the theme's accent colour. */
export function Logo({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="3.5 0 100 100" fill="var(--accent)" aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M36.15,30.97 L59.69,38.58 A12 12 0 0 1 60.00,38.69 L76.00,44.34 A6 6 0 0 1 76.00,55.66 L60.00,61.31 A12 12 0 0 1 59.69,61.42 L36.15,69.03 A20 20 0 1 1 36.15,30.97 Z M19.8 43 a3.2 3.2 0 1 0 6.4 0 a3.2 3.2 0 1 0 -6.4 0 Z"
      />
      <path d="M72 44 L97 26 A30 30 0 0 0 97 74 L72 56 Z" />
    </svg>
  )
}
