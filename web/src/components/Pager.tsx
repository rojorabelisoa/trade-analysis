export function Pager({
  page,
  size,
  total,
  onPage,
}: {
  page: number;
  size: number;
  total: number;
  onPage: (p: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) return null;
  return (
    <nav className="pager" aria-label="Pages">
      <button type="button" className="btn btn-secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        Précédent
      </button>
      <span>
        Page {page + 1} sur {pages}
      </span>
      <button
        type="button"
        className="btn btn-secondary"
        disabled={page >= pages - 1}
        onClick={() => onPage(page + 1)}
      >
        Suivant
      </button>
    </nav>
  );
}
