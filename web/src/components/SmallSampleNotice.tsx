import { SMALL_SAMPLE } from '../lib/format';

/** Rappel discret : un petit nombre de trades ne permet pas de conclure. */
export function SmallSampleNotice({ trades }: { trades: number }) {
  if (trades >= SMALL_SAMPLE) return null;
  return (
    <p className="notice-small">
      {trades} trade{trades > 1 ? 's' : ''} : échantillon trop petit pour conclure (moins de {SMALL_SAMPLE}).
      Ces chiffres mesurent le passé et ne promettent aucun gain.
    </p>
  );
}
