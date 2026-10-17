# TreasoryFlow

Trésorerie bancaire multiréglementaire pour une entité tunisienne, une entité de l'Union européenne et leur groupe. L'application est la console, l'API porte le socle, le moteur de règles et la consolidation.

Ce dépôt est un socle de travail. Les pondérations semées (`BCT-2014-14-BASELINE`, `CRR-LCR-2015-61-BASELINE`, `CRR-NSFR-BASELINE`) servent à rendre les calculs reproductibles. Elles doivent être validées sur les textes consolidés et le statut de chaque établissement avant tout usage prudentiel. Le produit ne constitue pas une certification réglementaire.

## Lancer en local

```bash
cd treasoryflow-api && ./gradlew test
./gradlew bootRun --args='--spring.profiles.active=local'
```

Dans un second terminal :

```bash
cd treasoryflow-app && ./gradlew bootRun
```

Console : http://localhost:8109 — API : http://localhost:8108 — OpenAPI : http://localhost:8108/swagger-ui.html

Le profil `local` utilise H2. Le profil par défaut attend PostgreSQL sur le port 5436 (`docker compose -f docker-compose.treasoryflow.yml up --build`).

L'API exige `X-Actor`, `X-Roles` et `X-Entity-Scope`. La console les envoie depuis le sélecteur d'acteur. En production ces en-têtes viennent de la passerelle d'identité, pas du navigateur.

## Séparation des règles

| Formule | Juridiction | Référence de travail |
| --- | --- | --- |
| `TN_LIQUIDITY_STOCK` | TN | Circulaire BCT n° 2014-14 |
| `EU_LCR` | UE | CRR et règlement délégué (UE) 2015/61 |
| `EU_NSFR` | UE | CRR, financement stable net |

Une formule n'est exécutée que si sa juridiction est celle de l'entité. Les poids, décotes et plafonds sont des paramètres de la version publiée. Une nouvelle version ne réécrit pas un calcul déjà arrêté : le rejeu repart de l'instantané stocké avec le résultat.

## Recette couverte par les tests

- REC-TN-001, REC-EU-001, REC-EU-002 : ratios reproductibles sur le jeu de référence.
- REC-GRP-001 : conversion TND/EUR historisée et élimination intragroupe unique.
- REC-GRP-002 : la liquidité tunisienne restreinte n'est pas proposée comme transférable.
- REC-SEC-001 : l'auteur ne valide pas sa propre opération.
- REC-INT-001 : rejouer un lot ne duplique pas les mouvements.
- REC-AUD-001 : le résultat reste lié à la version de règle, à l'empreinte des sources et au rapport.

## Spécification initiale

Cahier des charges fonctionnel et technique reproduit tel que cadré au démarrage du produit.

**TMS bancaire multiréglementaire — Tunisie et Union européenne**

- Référence : CDC-TMS-MULTI-001
- Version : 2.0 — Adaptation multijuridictionnelle
- Date : 17 octobre 2026
- Architecture : intégration à un core banking existant
- Périmètre : trésorerie, liquidité, opérations de marché, ALM, risques, comptabilité et reporting prudentiel

## 1. Objet du projet

Le projet consiste à développer une plateforme de gestion de trésorerie bancaire permettant de couvrir simultanément :

- Les besoins d'une banque opérant en Tunisie sous la supervision de la BCT.
- Les besoins d'une banque opérant dans l'Union européenne sous le cadre réglementaire qui lui est applicable.
- Les besoins d'un groupe bancaire possédant des entités dans les deux zones.
- Le système devra partager un socle fonctionnel commun tout en isolant les règles réglementaires, les habilitations, les données et les processus propres à chaque entité.

### 1.1 Objectifs métier

- Centraliser les soldes bancaires et les comptes correspondants.
- Consolider les positions en TND, EUR et autres devises.
- Gérer les opérations de trésorerie et leurs échéanciers.
- Prévoir les besoins de liquidité par date, devise et entité.
- Gérer les financements, placements et opérations de change.
- Contrôler les expositions et les limites.
- Calculer les ratios de liquidité applicables à chaque entité.
- Produire les états réglementaires et les éléments justificatifs.
- Suivre les écarts entre prévisions et réalisations.
- Assurer une piste d'audit et une séparation des tâches.
- Consolider les données d'un groupe sans neutraliser les contraintes locales de liquidité.
- Préparer l'évolution des règles sans réécrire l'ensemble du système.

## 2. Principes d'architecture réglementaire

### 2.1 Architecture logique

Le système comportera six couches :

- Systèmes sources : core banking, comptabilité, systèmes de paiement, référentiels de marché.
- Intégration : API, fichiers, messages, contrôles d'intégrité et reprise.
- Socle TMS commun : référentiels, positions, opérations, prévisions, rapprochement.
- Moteur de règles réglementaires : profils Tunisie, UE et profils nationaux européens.
- Consolidation groupe : agrégation, élimination des opérations intragroupe et analyses de transfert de liquidité.
- Restitution : tableaux de bord, reporting prudentiel, alertes et audit.

### 2.2 Principes impératifs

- Aucune formule réglementaire ne doit être codée directement dans les écrans.
- Toute règle doit posséder une juridiction, un périmètre, une version et des dates d'effet.
- Les calculs doivent être reproductibles.
- Les données sources doivent rester accessibles aux contrôleurs autorisés.
- Une modification réglementaire ne doit pas écraser les résultats historiques.
- Les règles locales et consolidées doivent être exécutées séparément.
- Une règle européenne ne doit pas être appliquée automatiquement à une filiale tunisienne, ni inversement.
- Les transferts intragroupe doivent respecter les restrictions juridiques, opérationnelles et prudentielles applicables.

## 3. Matrice réglementaire

| Domaine | Profil Tunisie | Profil Union européenne |
| --- | --- | --- |
| Supervision | BCT | BCE et/ou autorité nationale compétente selon l'établissement |
| Ratio de liquidité | Ratio tunisien et textes BCT applicables | LCR selon le CRR et le règlement délégué applicable |
| Financement stable | Selon les exigences locales applicables | NSFR selon le CRR et les règles applicables |
| Reporting prudentiel | États et formats requis par la BCT | COREP et autres reportings requis, selon le périmètre |
| Change | Réglementation tunisienne applicable | Régime du pays et de l'établissement concerné |
| Paiements | Systèmes et formats utilisés en Tunisie | SEPA, TARGET ou autres infrastructures selon le périmètre |
| Données personnelles | Droit tunisien applicable | RGPD et droit national applicable |
| Résilience numérique | Exigences locales et politiques bancaires | DORA pour les entités concernées |
| Consolidation | Règles locales et de groupe applicables | Règles prudentielles individuelles, sous-consolidées ou consolidées |

Important : les règles détaillées doivent être validées à partir des textes en vigueur, des instructions de déclaration et du statut réglementaire de chaque entité.

## 4. Exigences fonctionnelles communes

### M01 — Référentiels

Le système doit gérer :

- Entités juridiques et périmètres de consolidation.
- Comptes bancaires et correspondants.
- Devises, calendriers et conventions de marché.
- Taux de change et sources de valorisation.
- Contreparties et limites.
- Produits de trésorerie.
- Règles de date de valeur.
- Référentiels réglementaires versionnés.
- Chaque objet doit posséder un identifiant stable, un statut, des dates de validité et un historique des modifications.

### M02 — Intégration au core banking

Le système doit récupérer les soldes, mouvements, comptes et références de transaction.

Chaque flux doit être contrôlé, tracé et protégé contre les doubles imports.

Les soldes comptabilisés demeurent ceux du système bancaire de référence. Les positions calculées par le TMS doivent être identifiées comme consolidées, prévisionnelles ou simulées.

Les interfaces doivent supporter les mécanismes de reprise, de rejet, de rapprochement et de surveillance.

### M03 — Positions de trésorerie

Le système doit afficher :

- Soldes comptabilisés et disponibles.
- Positions par compte et correspondant.
- Positions par devise.
- Position consolidée en devise de reporting.
- Mouvements attendus.
- Opérations non réglées.
- Positions intrajournalières lorsque les données le permettent.
- Date de dernière actualisation.
- Les conversions doivent conserver le montant d'origine, le taux, la source, la date et la méthode de calcul.

### M04 — Opérations de marché

Le système doit couvrir les produits autorisés et utilisés par la banque :

- Change au comptant.
- Change à terme.
- Swaps de change.
- Placements interbancaires.
- Emprunts interbancaires.
- Repo et reverse repo, si concernés.
- Titres et garanties, si inclus dans le périmètre.
- Échéances, intérêts, règlements et confirmations.
- Chaque opération doit suivre un cycle de vie contrôlé : saisie, validation, transmission éventuelle, confirmation, règlement et rapprochement.

### M05 — Prévisions de liquidité

Le système doit calculer les positions projetées par compte, devise, date et entité.

Les horizons doivent couvrir l'intrajournalier, le quotidien, l'hebdomadaire, le mensuel et les horizons structurels utiles à l'ALM.

Les flux doivent être classés comme contractuels, estimés ou simulés. Les scénarios doivent pouvoir simuler des retards, des retraits, des variations de change et des indisponibilités de financement.

### M06 — Financement et ALM

Le système doit fournir :

- Détection des déficits.
- Identification des excédents.
- Plans de financement.
- Analyse du coût des ressources.
- Échéanciers actifs/passifs.
- Gaps périodiques et cumulés.
- Scénarios de stress.
- Analyse des concentrations.
- Sensibilités aux taux selon les modèles validés.
- Les recommandations de financement doivent être explicables et soumises aux validations requises.

### M07 — Limites et risques

Le système doit gérer les limites par contrepartie, produit, devise, entité et périmètre.

Les contrôles doivent permettre un avertissement, un blocage ou une dérogation selon les paramètres approuvés.

Les dépassements doivent être historisés, attribués à un responsable et suivis jusqu'à leur résolution.

### M08 — Rapprochement et comptabilité

Le système doit rapprocher les mouvements externes et internes à partir des références, montants, devises, dates et comptes.

Il doit gérer les suspens, les écarts, les justificatifs et les propositions d'écritures.

Les écritures définitives sont transmises au système comptable officiel, sauf architecture différente approuvée.

### M09 — Reporting et audit

Le système doit fournir des tableaux de bord par entité, devise, correspondant et périmètre de consolidation.

Il doit produire les rapports opérationnels, les rapports de risques et les états réglementaires applicables.

Chaque rapport doit conserver sa date de référence, ses paramètres, la version des règles et les données sources utilisées.

### M10 — Sécurité et administration

Le système doit assurer :

- Contrôle d'accès par rôle et périmètre.
- Authentification conforme à la politique de sécurité.
- Séparation des tâches.
- Validation à quatre yeux lorsque requise.
- Journal d'audit protégé.
- Gestion des incidents et des habilitations.
- Supervision des interfaces.
- Sauvegarde et restauration.

## 5. Exigences réglementaires spécifiques à la Tunisie

### TN-001 — Profil réglementaire BCT

Le système doit disposer d'un profil de calcul propre aux entités tunisiennes.

Ce profil doit être lié aux textes BCT applicables, à leurs dates d'effet et aux instructions de déclaration.

### TN-002 — Ratio de liquidité tunisien

Le moteur doit calculer le ratio selon la version réglementaire validée, à partir des actifs liquides, des sorties et des entrées de trésorerie pertinentes.

Il doit prendre en charge les catégories, pondérations, plafonds et contrôles prévus par les textes applicables.

Le ratio tunisien ne doit pas être remplacé automatiquement par le LCR européen.

### TN-003 — Comptes BCT et correspondants

Les comptes concernés doivent être identifiés par devise, statut, disponibilité, date de valeur et source.

Les contraintes d'utilisation ou de transfert doivent être paramétrables.

### TN-004 — Change et financement

Les opérations doivent respecter les autorisations, contrôles et procédures applicables aux entités tunisiennes.

Les contrôles réglementaires doivent être distincts des limites internes de la banque.

### TN-005 — États réglementaires

Les états doivent être produits selon les formats et calendriers de la BCT applicables à l'établissement.

Chaque état doit être rapproché des données sources et validé par un utilisateur autorisé.

## 6. Exigences réglementaires spécifiques à l'Union européenne

### EU-001 — LCR

Le système doit calculer le Liquidity Coverage Ratio selon les règles applicables du CRR et du règlement délégué (UE) 2015/61, dans leurs versions en vigueur.

Le moteur doit prendre en charge :

- Éligibilité des actifs liquides.
- Classification et valorisation des actifs.
- Décotes et plafonds.
- Sorties de trésorerie pondérées.
- Entrées de trésorerie pondérées.
- Plafonds sur les entrées, lorsque requis.
- Contrôles de cohérence.
- Détail explicatif du ratio.
- Le niveau réglementaire attendu et les modalités de déclaration doivent être configurés selon le statut de l'établissement et les exigences de son autorité compétente.

### EU-002 — NSFR

Le système doit calculer le Net Stable Funding Ratio selon les dispositions applicables du CRR.

Il doit gérer :

- Les catégories de financement disponible.
- Les catégories d'actifs et engagements.
- Les facteurs ASF et RSF applicables.
- Les échéances résiduelles.
- Les traitements réglementaires particuliers.
- Les rapprochements avec les données comptables.

### EU-003 — Reporting prudentiel

Le système doit préparer les données et les contrôles nécessaires aux états prudentiels requis.

L'interface de reporting doit prendre en compte les versions du cadre EBA, les modèles applicables, les taxonomies et les formats exigés à la date de déclaration.

Le TMS peut fournir les données sources et les calculs de liquidité sans se substituer à un moteur de reporting prudentiel plus large si la banque en possède déjà un.

### EU-004 — Données personnelles

Lorsque le RGPD est applicable, le système doit prendre en charge :

- Limitation des accès.
- Minimisation des données.
- Traçabilité des consultations sensibles.
- Conservation et suppression selon les règles approuvées.
- Gestion des incidents impliquant des données personnelles.
- Contrôles des transferts de données.

### EU-005 — Résilience numérique

Pour les entités soumises à DORA, les exigences applicables doivent être intégrées au dispositif d'exploitation :

- Gestion des risques TIC.
- Inventaire des actifs et dépendances.
- Gestion des incidents.
- Tests de résilience.
- Continuité d'activité.
- Gestion des prestataires TIC.
- Contrôle des accès et des changements.
- Preuves de tests et de restauration.

### EU-006 — Paiements et infrastructures

Les interfaces doivent être adaptées aux systèmes effectivement utilisés.

SEPA, TARGET, SWIFT et les autres infrastructures ne doivent être activés que lorsque le périmètre métier, les accès et les obligations de l'établissement le justifient.

## 7. Gestion des règles réglementaires

Chaque règle doit être enregistrée avec les attributs suivants :

| Attribut | Description |
| --- | --- |
| rule_id | Identifiant stable |
| jurisdiction | TN, UE ou juridiction nationale |
| entity_scope | Entité ou périmètre concerné |
| regulatory_reference | Texte ou instruction de référence |
| rule_version | Version du calcul |
| valid_from | Date de début d'application |
| valid_to | Date de fin d'application, le cas échéant |
| parameters | Pondérations, plafonds, seuils et paramètres |
| approval_status | Brouillon, validé, publié ou retiré |
| approved_by | Responsable de validation |
| test_reference | Référence des tests de conformité |

Toute nouvelle version doit être testée avant publication et ne doit pas altérer les résultats historiques déjà arrêtés.

## 8. Consolidation du groupe Tunisie–Europe

### GRP-001 — Consolidation des positions

Le système doit consolider les positions par entité, pays, devise et groupe.

Les soldes doivent être convertis avec des taux identifiés et historisés.

### GRP-002 — Élimination des opérations intragroupe

Les flux intragroupe doivent être identifiés afin d'éviter leur double comptage dans les consolidations.

Les éliminations comptables et prudentielles doivent être traitées séparément lorsque les règles diffèrent.

### GRP-003 — Contraintes de transfert

Une liquidité disponible dans une entité ne doit pas être considérée comme automatiquement transférable vers une autre entité.

Le système doit tenir compte des restrictions juridiques, des devises, des cut-off, des autorisations et des contraintes prudentielles applicables.

### GRP-004 — Reporting groupe

Le système doit permettre de comparer les indicateurs locaux et consolidés sans confondre leurs formules, périmètres ou seuils.

## 9. Modèle de données complémentaire

En complément des entités communes du TMS, les objets suivants sont obligatoires :

| Entité | Fonction |
| --- | --- |
| RegulatoryJurisdiction | Juridiction applicable |
| RegulatoryProfile | Profil de règles associé à une entité |
| RegulatoryRuleVersion | Version et validité d'une règle |
| RegulatoryParameter | Pondérations, plafonds et seuils |
| RegulatoryCalculationRun | Exécution d'un calcul réglementaire |
| RegulatoryResultDetail | Détail des composantes du résultat |
| RegulatoryReport | État produit et statut de validation |
| GroupConsolidationScope | Périmètre de consolidation |
| IntercompanyFlow | Flux intragroupe |
| CurrencyValuation | Valorisation et taux utilisés |
| ComplianceApproval | Validation de conformité |
| RegulatoryChangeLog | Historique des changements réglementaires |

## 10. Interfaces techniques

Les interfaces devront couvrir :

- Core banking.
- Comptabilité.
- Systèmes de paiement.
- Correspondants bancaires.
- Flux de marché.
- Référentiels de change.
- Systèmes de reporting.
- Gestion des identités.
- Outils de supervision.
- Chaque interface doit définir le protocole, le format, le calendrier, les contrôles, les statuts de traitement et les mécanismes de reprise.

Les formats tels que MT940, camt.053, ISO 20022 ou les formats de déclaration prudentielle ne doivent être utilisés que lorsqu'ils correspondent aux systèmes et obligations concernés.

## 11. Exigences non fonctionnelles

### Sécurité

- Authentification forte selon les exigences applicables.
- Contrôle d'accès au moindre privilège.
- Chiffrement des communications.
- Gestion sécurisée des secrets.
- Journalisation des opérations sensibles.
- Gestion des vulnérabilités.
- Tests de sécurité.

### Intégrité financière

- Calculs monétaires en précision décimale contrôlée.
- Historisation des taux et conventions.
- Contrôle des doubles imports.
- Traçabilité des corrections.
- Reproductibilité des calculs.
- Séparation des données réelles, prévisionnelles et simulées.

### Continuité

Les objectifs RTO et RPO doivent être définis par la banque. Les tests de sauvegarde, restauration, reprise et fonctionnement dégradé doivent être réalisés avant la mise en production.

### Performance

Les seuils de performance doivent être définis après étude de volumétrie, notamment pour les positions intrajournalières, les consolidations groupe et les calculs réglementaires.

## 12. Tests de recette spécifiques

### REC-TN-001 — Ratio tunisien

Le résultat doit correspondre au calcul de référence validé par la conformité tunisienne, à partir des mêmes données et de la même version des règles.

### REC-EU-001 — LCR européen

Le résultat doit correspondre au moteur de référence validé pour le périmètre européen, avec les mêmes données, paramètres et dates d'effet.

### REC-EU-002 — NSFR européen

Le résultat doit être reproductible à partir des catégories d'actifs, de passifs et des facteurs applicables.

### REC-GRP-001 — Consolidation

Les positions converties doivent être cohérentes et les opérations intragroupe ne doivent pas être comptées deux fois.

### REC-GRP-002 — Transfert de liquidité restreint

Une position disponible dans une entité soumise à restriction ne doit pas être proposée comme librement transférable sans contrôle explicite.

### REC-SEC-001 — Séparation des tâches

Un utilisateur ne peut pas approuver sa propre opération lorsque la règle des quatre yeux s'applique.

### REC-INT-001 — Reprise d'interface

La réexécution d'un flux ne doit provoquer ni perte ni duplication.

### REC-AUD-001 — Reproductibilité

Tout résultat réglementaire doit être associé à ses données sources, paramètres, versions de règles et validations.

## 13. Plan de mise en œuvre

### Phase 1 — Socle commun

- Référentiels.
- Intégration au core banking.
- Soldes et positions.
- Gestion des accès.
- Audit.

### Phase 2 — Trésorerie opérationnelle

- Opérations de change.
- Placements et emprunts.
- Échéanciers.
- Prévisions.
- Rapprochement.

### Phase 3 — Profil réglementaire tunisien

- Paramétrage BCT.
- Calcul du ratio local.
- États réglementaires.
- Validation indépendante.

### Phase 4 — Profil réglementaire européen

- LCR.
- NSFR.
- Interfaces de reporting prudentiel.
- Exigences de résilience et de protection des données applicables.

### Phase 5 — Consolidation internationale

- Positions groupe.
- Éliminations intragroupe.
- Scénarios de stress.
- Reporting consolidé.

### Phase 6 — Industrialisation

- Tests de sécurité et de charge.
- Tests de reprise.
- Recette métier et réglementaire.
- Mise en production progressive.

## 14. Livrables attendus

Le prestataire devra fournir :

- Spécifications fonctionnelles détaillées.
- Matrice réglementaire Tunisie–UE.
- Architecture technique.
- Modèle de données.
- Contrats d'API et formats d'interface.
- Catalogue des règles de gestion.
- Documentation des calculs réglementaires.
- Matrice des habilitations.
- Code source et scripts de déploiement.
- Plan de tests et preuves de recette.
- Dossier de sécurité.
- Documentation d'exploitation.
- Procédures de sauvegarde et de reprise.
- Guides utilisateurs et administrateurs.
- Procédures de mise à jour réglementaire.

## 15. Critères de réception définitive

La réception est conditionnée par :

- La conformité aux exigences fonctionnelles approuvées.
- La validation des interfaces au core banking.
- La réussite des tests critiques.
- La validation indépendante des calculs réglementaires.
- La traçabilité complète des résultats.
- La résolution des anomalies bloquantes.
- La disponibilité du code et de la documentation.
- La réussite des tests de sécurité et de restauration.
- L'approbation formelle des directions métier, risques, conformité et informatique.

## 16. Références réglementaires de travail

Les références ci-dessous constituent une base de cadrage et doivent être vérifiées dans leurs versions consolidées applicables avant contractualisation.

### Tunisie

- Circulaire BCT n° 2014-14 relative au ratio de liquidité et ses éventuelles modifications.
- Textes BCT relatifs aux risques, au contrôle interne, aux systèmes de paiement et aux opérations de change.
- Loi organique tunisienne n° 2004-63 relative à la protection des données à caractère personnel.

### Union européenne

- Règlement (UE) n° 575/2013 (CRR), dans sa version applicable.
- Règlement délégué (UE) 2015/61 relatif au LCR, dans sa version applicable.
- Cadre de reporting prudentiel de l'EBA et instructions de déclaration en vigueur.
- Règlement (UE) 2016/679 (RGPD), lorsque applicable.
- Règlement (UE) 2022/2554 (DORA), applicable depuis le 17 janvier 2025 aux entités dans son champ.

### Références officielles

EUR-Lex — CRR : https://eur-lex.europa.eu/eli/reg/2013/575/oj

EUR-Lex — LCR : https://eur-lex.europa.eu/eli/reg_del/2015/61/oj

EBA — Reporting frameworks : https://www.eba.europa.eu/risk-and-data-analysis/reporting/reporting-frameworks

EUR-Lex — RGPD : https://eur-lex.europa.eu/eli/reg/2016/679/oj

EUR-Lex — DORA : https://eur-lex.europa.eu/eli/reg/2022/2554/oj

Conclusion : le TMS doit être construit comme un produit unique doté de plusieurs profils réglementaires versionnés. Cette approche réduit les duplications fonctionnelles tout en conservant l'indépendance des règles tunisiennes, européennes et nationales. Elle ne constitue pas, à elle seule, une certification de conformité réglementaire.
