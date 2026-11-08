# trade-analysis

Détecteur de signaux d'entrée sur l'or et les matières premières (futures CME
via TradingView), avec la logique **Zone S/R → Liquidation → Entrée**, filtrée
par ton **biais fondamental** et le **calendrier économique**, plus un **backtest
prudent** pour vérifier qu'il y a un avantage *avant* de partager quoi que ce soit.

## La stratégie, en règles mesurables

| Étape | Timeframe | Règle codée |
|---|---|---|
| Contexte | H1 ou H4 | Swings (5 bougies de chaque côté), regroupés en zones si écart < 0,35 × ATR. Zone valide si ≥ 2 contacts. |
| Liquidation | 5m ou 15m | La mèche dépasse la zone d'au moins 0,2 × ATR(LTF), mais de moins de 1 × ATR(HTF) (au-delà, c'est une vraie cassure), puis la bougie **clôture de nouveau dans la zone**. |
| Entrée | 5m ou 15m | Dans les 12 bougies suivantes, une **clôture** casse le dernier swing LTF opposé (CHOCH). Le sweep est annulé si le prix repasse sous sa mèche. |
| Risque | — | Stop = mèche du sweep ± 0,2 × ATR(LTF). Objectif = 2R. Signal rejeté si le stop dépasse 1,5 × ATR(HTF). |
| Filtres | — | Biais fondamental (`bias.csv`), aucune entrée ±30 min autour d'une annonce à fort impact (`news.csv`), sessions en option. |

Tous ces chiffres sont des paramètres de départ (`Config` dans
`trade_analysis/strategy.py`), **pas des valeurs optimales**. Il faut les tester.

**Garantie anti look-ahead :** un swing n'est utilisé qu'après la clôture des
bougies qui le confirment. Une bougie HTF n'est utilisée qu'une fois clôturée.
Un test vérifie que couper les données ne change jamais un signal déjà émis.

## Installation

```bash
pip install -r requirements.txt
python -m pytest -q
```

## 1. Récupérer les données depuis TradingView

1. Ouvre par exemple `COMEX:GC1!` (or), `NYMEX:CL1!` (pétrole), `COMEX:SI1!` (argent).
2. Choisis le timeframe d'entrée (5m ou 15m) et fais défiler vers la gauche pour charger un maximum d'historique.
3. Menu du graphique (⋯) → **Exporter les données du graphique** → format horaire *UNIX timestamp* (ou ISO).
4. Dépose le fichier dans `data/` (ce dossier est ignoré par git).

Tu peux exporter aussi le H1/H4 (`--htf`), ou laisser le script le reconstruire
depuis le 5m/15m (`--preset`).

> ⚠️ `GC1!` est un contrat **continu**. Au changement de contrat, il y a un saut
> de prix qui peut créer de faux niveaux. Sur TradingView, active l'ajustement
> des contrats (*back-adjust*) dans les paramètres du symbole si ton abonnement le
> permet, ou évite les jours de changement de contrat.

## 2. Saisir ton fondamental (investing.com)

`bias.csv` contient ta lecture du marché. Chaque biais s'applique jusqu'à la ligne suivante :

```csv
date,symbol,bias,note
2026-01-12,GC,long,Fed dovish + DXY faible
2026-01-26,GC,short,CPI chaud + taux réels en hausse
```

Valeurs possibles : `long`, `short`, `neutral` (les deux sens autorisés, ou
aucun avec `--neutral-blocks`), `off` (pas de trading).

`news.csv` contient les annonces 3 étoiles du calendrier investing.com. **Règle
le fuseau d'investing.com sur GMT** pour copier les heures telles quelles :

```csv
time_utc,currency,impact,event
2026-01-13 13:30,USD,high,CPI
```

## 3. Backtester

```bash
# H1 contexte / 5m entrée
python -m trade_analysis backtest --ltf data/GC1_5m.csv --preset h1-5m --symbol GC \
    --bias bias.csv --news news.csv --cost 0.3 --out gc_h1_5m.trades.csv

# H4 contexte / 15m entrée
python -m trade_analysis backtest --ltf data/GC1_15m.csv --preset h4-15m --symbol GC \
    --bias bias.csv --news news.csv --cost 0.3
```

`--cost` = spread + commission + glissement, en points de prix (pour GC, 0,3 $
est un minimum réaliste). Le backtest entre à l'ouverture de la bougie
suivante. Si le stop et l'objectif sont touchés dans la même bougie, il compte
le stop. Les résultats sont donnés en R, avec un détail par session et par sens.

## 4. Obtenir le dernier signal

```bash
python -m trade_analysis scan --ltf data/GC1_5m.csv --preset h1-5m --symbol GC \
    --bias bias.csv --news news.csv --note "GC1! CME"
```

Le texte affiché est prêt à copier dans Telegram ou WhatsApp. Il contient
l'entrée, le stop, l'objectif, la raison du signal, la zone opposée la plus
proche et les annonces des prochaines 24 h.

## Démo

`examples/` contient des données **synthétiques** (marche aléatoire) pour
tester les commandes :

```bash
python -m trade_analysis backtest --ltf examples/demo_5m.csv --preset h1-5m \
    --bias examples/bias.csv --news examples/news.csv --cost 0.3
```

Le résultat est négatif, et c'est normal : un marché aléatoire n'a aucun
avantage à exploiter. C'est justement le contrôle : si ta stratégie fait à peine
mieux sur le vrai GC que sur ce bruit, elle n'a pas d'avantage.

## Avant de partager un signal

- Au moins **100 trades** en backtest, avec une espérance > 0 **après coûts**.
- Résultats stables sur plusieurs périodes : règle sur une année, vérifie sur l'année suivante.
- Ensuite, 1 à 2 mois en mode *scan* sans argent réel, en notant chaque signal.
- Ce n'est pas un conseil financier.
