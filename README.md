# Kiddo Auto Tapper

Application Android (bulle flottante + tap automatique dans n'importe quelle app),
compilée dans le cloud via GitHub Actions — pas besoin de PC ni d'Android Studio.

## ⚠️ À savoir avant de commencer

- Le tap dans une autre app n'est possible **que** via le Service d'Accessibilité :
  c'est une restriction de sécurité d'Android, aucune app ne peut la contourner.
- Certaines apps/jeux détectent et bloquent les outils d'accessibilité (anti-triche).
  L'utiliser dans une app tierce peut violer ses conditions d'utilisation.
- Teste sur un **vrai téléphone**, jamais sur un émulateur.

## 1. Créer le dépôt GitHub

1. Sur GitHub, crée un nouveau dépôt (public ou privé, peu importe) nommé par
   exemple `autotapper`
2. Upload **tout le contenu de ce zip** à la racine du dépôt — en gardant bien
   la structure des dossiers telle quelle (`app/`, `.github/`, etc.)

## 2. Laisser GitHub compiler

Dès que le fichier `.github/workflows/build.yml` est présent sur la branche
`main`, GitHub lance automatiquement la compilation. Tu peux aussi la relancer
manuellement :

1. Va dans l'onglet **Actions** de ton dépôt
2. Clique sur **Build APK** dans la liste à gauche
3. Bouton **Run workflow** → **Run workflow**

## 3. Récupérer l'APK compilé

1. Dans l'onglet **Actions**, clique sur l'exécution terminée (coche verte ✅)
2. Tout en bas de la page, section **Artifacts** → télécharge
   `AutoTapper-debug-apk` (fichier .zip contenant l'APK)
3. Dézippe sur ton téléphone, tu obtiens `app-debug.apk`
4. Installe-le (Android demandera d'autoriser "l'installation depuis des
   sources inconnues" la première fois — normal, l'app ne vient pas du
   Play Store)

⏱️ Une compilation prend en général 2 à 4 minutes.

## 4. Utilisation de l'app

1. **Étape 1** : bouton "Autoriser l'affichage par-dessus les autres apps" →
   active le switch pour Kiddo Auto Tapper → reviens en arrière
2. **Étape 2** : bouton "Activer le service d'accessibilité" → trouve
   "Kiddo Auto Tapper" dans la liste → active-le → confirme le message
   d'avertissement d'Android (normal, standard pour TOUS les services
   d'accessibilité) → reviens en arrière
3. **Étape 3** : bouton "Lancer la bulle" → une petite bulle rose apparaît
4. **Glisse** la bulle à l'endroit exact où tu veux que ça tape
5. **Tape une fois** (sans glisser) sur la bulle → un petit panneau s'ouvre
6. Règle l'intervalle en millisecondes (50 par défaut, minimum réaliste ~20)
7. **Démarrer** / **Arrêter** — "Quitter la bulle" ferme tout

## Si la compilation échoue

Va dans **Actions** → clique sur l'exécution en échec (croix rouge ❌) → clique
sur l'étape qui a un problème pour voir le message d'erreur exact. Envoie-moi
une capture d'écran, je pourrai identifier le souci directement.

## Limites à connaître

- Intervalle minimum fiable : ~20ms — en dessous, Android fusionne ou ignore
  les gestes.
- Si "Démarrer" affiche "Active le service d'accessibilité !", l'étape 2 n'a
  pas été validée (ou Android l'a désactivée après une mise à jour — ça arrive,
  il faut parfois la réactiver manuellement).
