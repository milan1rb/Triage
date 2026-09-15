# Triage — compilation sans ordinateur

Projet Android natif qui range les conversations WhatsApp dans des dossiers
permanents, avec une vue par défaut, et renvoie vers WhatsApp pour discuter.

Tout se fait depuis le téléphone : GitHub compile l'APK sur ses serveurs, tu le
télécharges et tu l'installes. Aucun ordinateur, aucun Android Studio.

## Les 10 fichiers du projet

```
settings.gradle
build.gradle
gradle.properties
.github/workflows/build.yml
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/triage/MainActivity.kt
app/src/main/java/com/example/triage/WhatsAppListenerService.kt
app/src/main/assets/index.html
```

Les chemins comptent. Un fichier au mauvais endroit et la compilation échoue.

## Étape 1 — Créer le dépôt

Sur github.com depuis Chrome, en mode ordinateur si l'affichage mobile gêne
(menu ⋮ → Site pour ordinateur) :

1. Crée un compte si tu n'en as pas
2. Bouton **New repository**, nomme-le `triage`, laisse-le **Private**
3. Coche **Add a README file**, puis **Create repository**

Tu ne partages jamais de token avec qui que ce soit. Tu n'en as pas besoin ici.

## Étape 2 — Déposer les fichiers

Télécharge d'abord les fichiers sur ton téléphone depuis la conversation.

Dans le dépôt : **Add file → Upload files**. Tu peux en envoyer plusieurs d'un
coup, mais l'envoi ne recrée pas les dossiers. Le plus simple est de créer
chaque fichier à la main :

**Add file → Create new file**, et dans le champ du nom, tape le chemin complet
avec les barres obliques. GitHub crée les dossiers automatiquement :

```
app/src/main/java/com/example/triage/MainActivity.kt
```

Colle le contenu, puis **Commit changes**. Répète pour les 9 fichiers.

C'est la partie fastidieuse — compte une petite heure. Elle ne se fait qu'une fois.

## Étape 3 — Récupérer l'APK

Le premier commit déclenche la compilation. Onglet **Actions** du dépôt :

- point orange : en cours, compte 3 à 5 minutes
- coche verte : c'est prêt
- croix rouge : ouvre la ligne en échec, le message d'erreur est en bas du log.
  Copie-le-moi, je te dis quoi corriger.

Une fois vert, ouvre la ligne du build et descends jusqu'à **Artifacts** :
`triage-apk`. Télécharge-le. C'est un ZIP, à extraire avec le gestionnaire de
fichiers du téléphone (Files by Google sait le faire).

## Étape 4 — Installer

Ouvre `app-debug.apk`. Android demandera l'autorisation d'installer depuis une
source inconnue : accepte pour ton gestionnaire de fichiers.

Au premier lancement, l'app demande l'accès aux notifications et ouvre l'écran
système. Active Triage dans la liste.

Les conversations apparaissent au fur et à mesure des messages reçus — l'app ne
voit rien de ce qui est arrivé avant son installation.

## Modifier l'app ensuite

Édite le fichier directement sur GitHub (icône crayon), commit, et une nouvelle
compilation démarre. Nouvel APK cinq minutes plus tard. C'est ton cycle de
développement complet, entièrement depuis le téléphone.

Pour l'interface, tu peux aussi tester `app/src/main/assets/index.html` dans
Chrome : il tourne avec des conversations factices, sans rien installer.

## Ce que l'app fait

- Dossiers permanents : nom, couleur, mots-clés de classement
- Dossier par défaut, qui s'ouvre au lancement et le reste
- Onglet « À classer » pour ce qui n'a pas encore de règle
- Compteurs de non-lus par dossier
- Ouverture dans WhatsApp, retour par le bouton arrière

## Limites, à assumer dans le rendu

- Pas d'historique : seulement ce qui arrive après l'installation
- Dépend des notifications : une conversation en sourdine ne produit rien
- Les notifications donnent un nom, pas un numéro — l'ouverture directe d'une
  conversation demande de saisir le numéro une fois, ensuite il est mémorisé
- Pas de deep link pour les groupes : on ouvre WhatsApp sans cibler
- Android uniquement : iOS n'expose aucun équivalent, ce qui est un bon point de
  comparaison sur les modèles de permissions
- Lecture seule : l'app n'envoie pas de messages
