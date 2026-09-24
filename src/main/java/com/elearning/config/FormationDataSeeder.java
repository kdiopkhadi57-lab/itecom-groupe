package com.elearning.config;

import com.elearning.entity.*;
import com.elearning.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Idempotent seeder for the "Comptabilité & Gestion", "Sage-Femme d'État", "Marketing Digital"
 * and "Développement Personnel" formations: adds dedicated teachers, at least 3 courses per
 * formation, lesson content, and enrolls demo students.
 * Runs on every startup but only creates/updates data as needed (find-or-create, then sync content).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FormationDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking formation demo data (Comptabilité & Gestion / Sage-Femme d'État / Marketing Digital / Développement Personnel)...");

        User comptaTeacher = ensureUser("Fatou", "Ndiaye", "comptabilite.teacher@elearning.com", "Teacher@2024",
            Role.ROLE_TEACHER, "Expert-comptable et formatrice en comptabilité et gestion d'entreprise");

        User sageFemmeTeacher = ensureUser("Awa", "Sarr", "sagefemme.teacher@elearning.com", "Teacher@2024",
            Role.ROLE_TEACHER, "Sage-femme d'État, formatrice en maïeutique et santé maternelle");

        // Comptabilité & Gestion - au moins 3 cours
        ensureCourse("Comptabilité Générale - Les Fondamentaux",
            "Apprenez les principes de base de la comptabilité générale : plan comptable, bilan, compte de résultat et écritures comptables.",
            "comptabilite", "BEGINNER", comptaTeacher, comptaCours1Lessons());

        ensureCourse("Gestion Financière et Analyse de Bilan",
            "Maîtrisez l'analyse financière d'une entreprise : lecture des états financiers, ratios financiers et tableaux de financement.",
            "comptabilite", "INTERMEDIATE", comptaTeacher, comptaCours2Lessons());

        ensureCourse("Fiscalité d'Entreprise et Déclarations",
            "Comprenez le système fiscal des entreprises : impôt sur les sociétés, TVA, charges sociales et obligations déclaratives.",
            "comptabilite", "INTERMEDIATE", comptaTeacher, comptaCours3Lessons());

        // Sage-Femme d'État - au moins 3 cours
        ensureCourse("Anatomie et Physiologie de la Grossesse",
            "Découvrez l'anatomie de l'appareil reproducteur féminin, la physiologie de la grossesse et le développement fœtal.",
            "sage-femme", "BEGINNER", sageFemmeTeacher, sageFemmeCours1Lessons());

        ensureCourse("Suivi Prénatal et Accouchement",
            "Apprenez le protocole de consultation prénatale, les phases de l'accouchement et les techniques d'accompagnement de la parturiente.",
            "sage-femme", "INTERMEDIATE", sageFemmeTeacher, sageFemmeCours2Lessons());

        ensureCourse("Soins du Nouveau-né et Post-partum",
            "Maîtrisez l'examen clinique du nouveau-né, l'allaitement maternel, la surveillance du post-partum et le suivi pédiatrique précoce.",
            "sage-femme", "INTERMEDIATE", sageFemmeTeacher, sageFemmeCours3Lessons());

        // Étudiants - formation Comptabilité & Gestion
        List<User> comptaStudents = List.of(
            ensureUser("Aminata", "Diop", "compta.etudiant1@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiante en Comptabilité & Gestion"),
            ensureUser("Cheikh", "Fall", "compta.etudiant2@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiant en Comptabilité & Gestion"),
            ensureUser("Ousmane", "Ndiaye", "compta.etudiant3@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiant en Comptabilité & Gestion")
        );
        enrollInCategory(comptaStudents, "comptabilite");
        removeEnrollmentsInCategory(comptaStudents, "sage-femme");

        // Étudiantes - formation Sage-Femme d'État
        List<User> sageFemmeStudents = List.of(
            ensureUser("Mariama", "Ba", "sagefemme.etudiant1@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiante en formation Sage-Femme d'État"),
            ensureUser("Ndeye", "Sy", "sagefemme.etudiant2@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiante en formation Sage-Femme d'État"),
            ensureUser("Khady", "Diouf", "sagefemme.etudiant3@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiante en formation Sage-Femme d'État")
        );
        enrollInCategory(sageFemmeStudents, "sage-femme");
        removeEnrollmentsInCategory(sageFemmeStudents, "comptabilite");

        User marketingTeacher = ensureUser("Mariétou", "Diagne", "marketing.teacher@elearning.com", "Teacher@2024",
            Role.ROLE_TEACHER, "Consultante en marketing digital et stratégie de communication, spécialisée en growth marketing et réseaux sociaux");

        User devPersoTeacher = ensureUser("Ibrahima", "Sow", "devperso.teacher@elearning.com", "Teacher@2024",
            Role.ROLE_TEACHER, "Coach certifié en développement personnel et leadership, formateur en gestion du temps et intelligence émotionnelle");

        // Marketing Digital - au moins 3 cours
        ensureCourse("Fondamentaux du Marketing Digital",
            "Découvrez les bases du marketing digital : écosystème digital, tunnel de conversion, persona d'acheteur et stratégie de contenu.",
            "marketing-digital", "BEGINNER", marketingTeacher, marketingCours1Lessons());

        ensureCourse("SEO et Growth Marketing",
            "Maîtrisez le référencement naturel (SEO) et les leviers du growth marketing pour générer du trafic qualifié durablement.",
            "marketing-digital", "INTERMEDIATE", marketingTeacher, marketingCours2Lessons());

        ensureCourse("Réseaux Sociaux et Publicité en Ligne",
            "Apprenez à construire une présence efficace sur les réseaux sociaux et à piloter des campagnes publicitaires rentables (Facebook, Instagram, TikTok).",
            "marketing-digital", "INTERMEDIATE", marketingTeacher, marketingCours3Lessons());

        // Développement Personnel - au moins 3 cours
        ensureCourse("Les Fondations du Développement Personnel",
            "Posez les bases de votre développement personnel : état d'esprit de croissance, roue de la vie, valeurs et objectifs SMART.",
            "developpement-personnel", "BEGINNER", devPersoTeacher, devPersoCours1Lessons());

        ensureCourse("Gestion du Temps et Productivité",
            "Apprenez à prioriser, vous concentrer et organiser votre semaine grâce à la matrice d'Eisenhower, la technique Pomodoro et la méthode GTD.",
            "developpement-personnel", "INTERMEDIATE", devPersoTeacher, devPersoCours2Lessons());

        ensureCourse("Confiance en Soi et Intelligence Émotionnelle",
            "Développez votre confiance en soi et votre intelligence émotionnelle grâce à la communication assertive et la méthode DESC.",
            "developpement-personnel", "INTERMEDIATE", devPersoTeacher, devPersoCours3Lessons());

        // Étudiants - formation Marketing Digital
        List<User> marketingStudents = List.of(
            ensureUser("Adama", "Faye", "marketing.etudiant1@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiant en Marketing Digital"),
            ensureUser("Bineta", "Diallo", "marketing.etudiant2@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiante en Marketing Digital"),
            ensureUser("Modou", "Seck", "marketing.etudiant3@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiant en Marketing Digital")
        );
        enrollInCategory(marketingStudents, "marketing-digital");

        // Étudiants - formation Développement Personnel
        List<User> devPersoStudents = List.of(
            ensureUser("Astou", "Gaye", "devperso.etudiant1@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiante en Développement Personnel"),
            ensureUser("Pape", "Diagne", "devperso.etudiant2@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiant en Développement Personnel"),
            ensureUser("Coumba", "Ndoye", "devperso.etudiant3@elearning.com", "Student@2024", Role.ROLE_STUDENT, "Étudiante en Développement Personnel")
        );
        enrollInCategory(devPersoStudents, "developpement-personnel");

        log.info("Formation demo data ready: Comptabilité & Gestion / Sage-Femme d'État / Marketing Digital / Développement Personnel (password: *@2024)");
    }

    private User ensureUser(String firstName, String lastName, String email, String rawPassword, Role role, String bio) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(
            User.builder()
                .firstName(firstName).lastName(lastName).email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role).enabled(true).registrationStatus("APPROVED").bio(bio).build()
        ));
    }

    private void ensureCourse(String title, String description, String category, String level, User teacher, List<LessonSpec> lessons) {
        Course course = courseRepository.findByTitle(title).orElseGet(() ->
            courseRepository.save(Course.builder()
                .title(title).description(description)
                .category(category).level(level)
                .teacher(teacher).published(true).build()));

        List<Lesson> existingLessons = lessonRepository.findByCourseOrderByOrderIndexAsc(course);

        for (int i = 0; i < lessons.size(); i++) {
            LessonSpec spec = lessons.get(i);
            Lesson lesson = existingLessons.stream()
                .filter(l -> l.getTitle().equals(spec.title()))
                .findFirst()
                .orElseGet(() -> Lesson.builder().title(spec.title()).course(course).build());

            boolean isExcelExercise = spec.type() == Lesson.LessonType.EXCEL_EXERCISE;
            lesson.setDescription(isExcelExercise ? spec.content() : spec.title() + " - " + title);
            lesson.setContent(isExcelExercise ? null : spec.content());
            lesson.setType(spec.type());
            lesson.setDuration(spec.duration());
            lesson.setOrderIndex(i);
            lesson.setStarterCode(spec.starterCode());
            lessonRepository.save(lesson);
        }
    }

    private void enrollInCategory(List<User> students, String category) {
        List<Course> courses = courseRepository.findByCategory(category);
        for (User student : students) {
            boolean changed = false;
            for (Course course : courses) {
                if (!student.getEnrolledCourses().contains(course)) {
                    student.getEnrolledCourses().add(course);
                    changed = true;
                }
            }
            if (changed) userRepository.save(student);
        }
    }

    private void removeEnrollmentsInCategory(List<User> students, String category) {
        List<Course> courses = courseRepository.findByCategory(category);
        for (User student : students) {
            boolean changed = student.getEnrolledCourses().removeAll(courses);
            if (changed) userRepository.save(student);
        }
    }

    private record LessonSpec(String title, Lesson.LessonType type, int duration, String content, String starterCode) {
        LessonSpec(String title, Lesson.LessonType type, int duration, String content) {
            this(title, type, duration, content, null);
        }
    }

    // ===================== Comptabilité & Gestion =====================

    private List<LessonSpec> comptaCours1Lessons() {
        return List.of(
            new LessonSpec("Introduction à la comptabilité générale", Lesson.LessonType.VIDEO, 20, """
                La comptabilité générale est une technique quantitative de collecte, de traitement et de communication d'informations financières concernant une entreprise. Elle permet de répondre à une question essentielle : que possède l'entreprise, que doit-elle, et quel est le résultat de son activité ?

                Objectifs de la comptabilité :
                - Enregistrer chronologiquement toutes les opérations financières de l'entreprise (achats, ventes, paiements, encaissements...)
                - Produire des documents de synthèse (bilan, compte de résultat) permettant d'évaluer la situation financière
                - Fournir une information fiable aux dirigeants, associés, banques, administration fiscale et autres partenaires

                Les grands principes comptables :
                1. Principe de la partie double : chaque opération est enregistrée à la fois au débit d'un compte et au crédit d'un autre, pour un même montant.
                2. Principe de prudence : on ne doit pas surestimer les produits ni sous-estimer les charges et les dettes.
                3. Principe de continuité d'exploitation : on suppose que l'entreprise va poursuivre son activité dans un avenir prévisible.
                4. Principe de l'image fidèle : les comptes doivent donner une image fidèle du patrimoine, de la situation financière et du résultat de l'entreprise.
                5. Principe de permanence des méthodes : les méthodes comptables doivent être appliquées de manière constante d'un exercice à l'autre.

                L'organisation comptable repose sur trois supports principaux :
                - Le journal : enregistrement chronologique de toutes les opérations
                - Le grand livre : regroupement des opérations par compte
                - La balance : récapitulatif de tous les comptes avec leurs soldes

                Dans les leçons suivantes, nous allons découvrir le plan comptable, apprendre à enregistrer des écritures en partie double, puis construire le bilan et le compte de résultat d'une entreprise.
                """),
            new LessonSpec("Le plan comptable et la classification des comptes", Lesson.LessonType.PDF, 25, """
                Le plan comptable est un référentiel qui liste et organise l'ensemble des comptes utilisés par une entreprise pour enregistrer ses opérations. Dans l'espace OHADA (Afrique de l'Ouest et Centrale), c'est le SYSCOHADA révisé qui s'applique.

                Structure générale du plan comptable (classes de comptes) :
                - Classe 1 : Comptes de ressources durables (capital, réserves, emprunts, provisions)
                - Classe 2 : Comptes d'actif immobilisé (terrains, bâtiments, matériel, immobilisations financières)
                - Classe 3 : Comptes de stocks (marchandises, matières premières, produits finis)
                - Classe 4 : Comptes de tiers (clients, fournisseurs, État, personnel)
                - Classe 5 : Comptes de trésorerie (banques, caisse, titres de placement)
                - Classe 6 : Comptes de charges (achats, services extérieurs, charges de personnel, dotations)
                - Classe 7 : Comptes de produits (ventes, production stockée, subventions)
                - Classe 8 : Comptes des autres charges et produits (hors activités ordinaires)

                Logique de numérotation :
                Chaque compte est identifié par un numéro qui indique sa classe et sa nature. Par exemple :
                - 101 : Capital social
                - 211 : Frais de développement (immobilisation)
                - 401 : Fournisseurs
                - 411 : Clients
                - 512 : Banques
                - 601 : Achats de marchandises
                - 701 : Ventes de marchandises

                Cette codification permet de classer toutes les opérations de façon homogène et de produire facilement les états financiers (bilan = comptes de classes 1 à 5, compte de résultat = comptes de classes 6 et 7).

                Exercice de réflexion : pour chacune des opérations suivantes, identifiez la classe de compte concernée : achat de matières premières, paiement d'un salaire, encaissement d'un client, achat d'un véhicule de livraison, emprunt bancaire à 5 ans.
                """),
            new LessonSpec("Les écritures comptables : débit et crédit", Lesson.LessonType.VIDEO, 30, """
                Le principe de la partie double est le fondement de la comptabilité : toute opération est enregistrée dans au moins deux comptes, une fois au débit et une fois au crédit, pour un montant identique. Le total des débits doit toujours être égal au total des crédits.

                Règles de fonctionnement des comptes :
                - Comptes d'actif (classes 2, 3, 4 clients, 5) : augmentent au débit, diminuent au crédit
                - Comptes de passif (classes 1, 4 fournisseurs) : augmentent au crédit, diminuent au débit
                - Comptes de charges (classe 6) : augmentent au débit
                - Comptes de produits (classe 7) : augmentent au crédit

                Exemples d'écritures courantes :

                1. Achat de marchandises à crédit (10 000) :
                   Débit  601 Achats de marchandises   10 000
                   Crédit 401 Fournisseurs                      10 000

                2. Vente de marchandises au comptant (15 000) :
                   Débit  571 Caisse                   15 000
                   Crédit 701 Ventes de marchandises            15 000

                3. Paiement d'un fournisseur par banque (10 000) :
                   Débit  401 Fournisseurs             10 000
                   Crédit 521 Banque                            10 000

                4. Encaissement d'une créance client (8 000) :
                   Débit  521 Banque                    8 000
                   Crédit 411 Clients                            8 000

                Méthode pour enregistrer une écriture :
                1. Identifier les comptes concernés par l'opération
                2. Déterminer si chaque compte augmente ou diminue
                3. Appliquer la règle débit/crédit selon la nature du compte
                4. Vérifier l'équilibre : total débit = total crédit

                La maîtrise de ces écritures de base est indispensable avant d'aborder le bilan et le compte de résultat dans la prochaine leçon.
                """),
            new LessonSpec("Pratique : enregistrer un journal comptable (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 20, """
                Mettez en pratique le principe de la partie double dans le tableur intégré.

                Un tableau "Journal comptable" est déjà prêt avec les colonnes Date, N° Compte, Libellé, Débit et Crédit, ainsi qu'une ligne TOTAUX (ligne 8) qui calcule automatiquement la somme des colonnes Débit et Crédit grâce à la formule =SOMME(...).

                Enregistrez les 3 opérations suivantes dans les lignes 2 à 4 (une opération = deux lignes, une au débit et une au crédit) :

                1. 01/01 - Apport en capital de l'exploitant par chèque bancaire : 500 000
                   Débit  521 Banque         500 000
                   Crédit 101 Capital                  500 000

                2. 05/01 - Achat de marchandises à crédit auprès d'un fournisseur : 120 000
                   Débit  601 Achats de marchandises   120 000
                   Crédit 401 Fournisseurs                       120 000

                3. 10/01 - Vente de marchandises au comptant, encaissée en caisse : 200 000
                   Débit  571 Caisse           200 000
                   Crédit 701 Ventes de marchandises             200 000

                Une fois toutes les lignes saisies, vérifiez que la ligne TOTAUX affiche bien le même montant en Débit et en Crédit (820 000) : c'est la vérification de l'équilibre de la partie double. Cliquez ensuite sur "Marquer comme terminé".
                """, """
                {"cells":{
                  "A1":{"raw":"Date","bold":true},
                  "B1":{"raw":"N° Compte","bold":true},
                  "C1":{"raw":"Libellé","bold":true},
                  "D1":{"raw":"Débit","bold":true},
                  "E1":{"raw":"Crédit","bold":true},
                  "A8":{"raw":"TOTAUX","bold":true},
                  "D8":{"raw":"=SOMME(D2:D7)","bold":true,"format":"currency"},
                  "E8":{"raw":"=SOMME(E2:E7)","bold":true,"format":"currency"}
                }}
                """),
            new LessonSpec("Le bilan et le compte de résultat", Lesson.LessonType.PDF, 25, """
                Le bilan et le compte de résultat sont les deux états financiers fondamentaux produits à la clôture de l'exercice comptable.

                LE BILAN
                Le bilan est une photographie du patrimoine de l'entreprise à une date donnée. Il se présente en deux colonnes qui s'équilibrent toujours :

                ACTIF (ce que possède l'entreprise) :
                - Actif immobilisé : immobilisations corporelles, incorporelles et financières
                - Actif circulant : stocks, créances clients, disponibilités (banque, caisse)

                PASSIF (comment ce patrimoine est financé) :
                - Capitaux propres : capital, réserves, résultat de l'exercice
                - Dettes financières : emprunts
                - Passif circulant : dettes fournisseurs, dettes fiscales et sociales

                Règle d'or : Total ACTIF = Total PASSIF

                LE COMPTE DE RÉSULTAT
                Le compte de résultat retrace l'activité de l'entreprise sur une période (l'exercice) en opposant :
                - Les CHARGES (classe 6) : ce que l'entreprise a consommé (achats, salaires, loyers, impôts...)
                - Les PRODUITS (classe 7) : ce que l'entreprise a généré (ventes, prestations de services, subventions...)

                La différence Produits - Charges donne le RÉSULTAT NET :
                - Si Produits > Charges → Bénéfice
                - Si Charges > Produits → Perte

                Lien entre les deux documents :
                Le résultat net calculé dans le compte de résultat vient enrichir (bénéfice) ou diminuer (perte) les capitaux propres dans le bilan. C'est ce qui assure la cohérence entre les deux documents.

                Exemple simplifié :
                Une entreprise réalise 100 000 de chiffre d'affaires (produit) et supporte 70 000 de charges. Son résultat net est de 30 000 (bénéfice), qui viendra augmenter ses capitaux propres au bilan de clôture.
                """),
            new LessonSpec("Quiz : Les bases de la comptabilité", Lesson.LessonType.QUIZ, 15, """
                Ce quiz vous permet de vérifier votre compréhension des notions essentielles abordées dans ce module :

                - Le principe de la partie double et la règle débit/crédit
                - La structure du plan comptable SYSCOHADA (classes 1 à 8)
                - La différence entre un compte d'actif, de passif, de charge et de produit
                - La construction et l'équilibre du bilan (Actif = Passif)
                - Le calcul du résultat net à partir du compte de résultat (Produits - Charges)

                Avant de répondre, relisez si besoin les leçons précédentes : "Introduction à la comptabilité générale", "Le plan comptable et la classification des comptes", "Les écritures comptables : débit et crédit" et "Le bilan et le compte de résultat".

                Conseil : pour chaque question, demandez-vous d'abord à quelle classe de compte appartient l'élément concerné (1 à 8), puis appliquez la règle de fonctionnement correspondante (augmentation au débit ou au crédit).

                Bonne chance !
                """)
        );
    }

    private List<LessonSpec> comptaCours2Lessons() {
        return List.of(
            new LessonSpec("Lecture des états financiers", Lesson.LessonType.VIDEO, 25, """
                Savoir lire les états financiers d'une entreprise est une compétence clé pour tout gestionnaire, comptable ou analyste financier. Les trois documents principaux à maîtriser sont le bilan, le compte de résultat et le tableau des flux de trésorerie.

                LE BILAN : une photo à un instant T
                Le bilan présente la situation patrimoniale de l'entreprise à la date de clôture. Pour bien le lire, il faut s'intéresser à :
                - La structure de l'actif : part des immobilisations vs actif circulant (stocks, créances, trésorerie)
                - La structure du passif : part des capitaux propres vs dettes (financières et d'exploitation)
                - Le niveau d'endettement : ratio dettes / capitaux propres

                LE COMPTE DE RÉSULTAT : un film sur une période
                Le compte de résultat retrace les performances de l'entreprise sur l'exercice. On distingue plusieurs niveaux de résultat :
                - La marge commerciale (pour les entreprises de négoce) = Ventes - Coût d'achat des marchandises vendues
                - La valeur ajoutée = Production - Consommations intermédiaires
                - L'excédent brut d'exploitation (EBE) = Valeur ajoutée - Charges de personnel - Impôts et taxes
                - Le résultat d'exploitation = EBE - Dotations aux amortissements et provisions + Reprises
                - Le résultat net = Résultat d'exploitation + Résultat financier + Résultat HAO - Impôt sur les sociétés

                LE TABLEAU DES FLUX DE TRÉSORERIE
                Il explique la variation de la trésorerie entre deux exercices en distinguant trois types de flux :
                - Flux de trésorerie liés à l'activité (exploitation)
                - Flux de trésorerie liés à l'investissement
                - Flux de trésorerie liés au financement

                Méthode de lecture recommandée :
                1. Commencer par le compte de résultat pour comprendre la performance globale
                2. Analyser le bilan pour comprendre la structure financière
                3. Vérifier la cohérence avec le tableau de flux de trésorerie (le résultat ne signifie pas toujours qu'il y a de la trésorerie disponible !)

                Dans les leçons suivantes, nous allons calculer et interpréter les principaux ratios financiers issus de ces documents.
                """),
            new LessonSpec("Analyse de la rentabilité et des ratios financiers", Lesson.LessonType.PDF, 30, """
                Les ratios financiers permettent de transformer les données brutes des états financiers en indicateurs comparables, qui facilitent l'analyse de la performance et de la santé financière d'une entreprise.

                RATIOS DE RENTABILITÉ
                - Taux de marge commerciale = Marge commerciale / Chiffre d'affaires HT × 100
                - Taux de marge nette = Résultat net / Chiffre d'affaires × 100
                - Rentabilité économique (ROA - Return on Assets) = Résultat d'exploitation / Total Actif × 100
                - Rentabilité financière (ROE - Return on Equity) = Résultat net / Capitaux propres × 100

                Le ROE est particulièrement suivi par les actionnaires : il mesure combien l'entreprise génère de bénéfice pour chaque unité monétaire investie par les propriétaires.

                RATIOS DE LIQUIDITÉ
                - Ratio de liquidité générale = Actif circulant / Dettes à court terme (doit être > 1)
                - Ratio de liquidité immédiate = (Trésorerie + Placements) / Dettes à court terme

                Ces ratios indiquent la capacité de l'entreprise à honorer ses dettes à court terme avec ses actifs les plus liquides.

                RATIOS DE STRUCTURE FINANCIÈRE
                - Ratio d'autonomie financière = Capitaux propres / Total Passif (un ratio élevé indique une faible dépendance aux dettes)
                - Ratio d'endettement = Dettes financières / Capitaux propres

                RATIOS DE GESTION (rotation)
                - Délai de rotation des stocks = (Stock moyen / Coût d'achat des marchandises vendues) × 360 jours
                - Délai de règlement clients = (Créances clients / CA TTC) × 360 jours
                - Délai de règlement fournisseurs = (Dettes fournisseurs / Achats TTC) × 360 jours

                Ces délais permettent d'analyser le cycle d'exploitation : plus le délai clients est court et le délai fournisseurs long, plus le besoin en fonds de roulement est faible.

                Exemple d'interprétation :
                Une entreprise avec un ROE de 15 %, un ratio de liquidité générale de 1,8 et un ratio d'autonomie financière de 45 % présente une rentabilité satisfaisante, une bonne liquidité, mais un niveau d'endettement à surveiller.

                Dans la prochaine leçon, nous verrons comment ces notions s'intègrent dans le tableau de financement et le calcul du besoin en fonds de roulement (BFR).
                """),
            new LessonSpec("Tableau de financement et besoin en fonds de roulement", Lesson.LessonType.VIDEO, 30, """
                Le tableau de financement (ou tableau emplois-ressources) permet d'analyser comment l'entreprise a financé ses investissements et comment ont évolué ses grands équilibres financiers entre deux exercices.

                LE FONDS DE ROULEMENT (FR)
                Le fonds de roulement représente l'excédent des ressources stables sur les emplois stables :
                FR = Ressources stables (capitaux propres + dettes financières à long terme) - Emplois stables (actif immobilisé)

                Un FR positif signifie que les ressources durables financent intégralement les immobilisations, et qu'il reste un excédent pour financer le cycle d'exploitation. Un FR négatif est un signal d'alerte (sauf cas particuliers comme la grande distribution).

                LE BESOIN EN FONDS DE ROULEMENT (BFR)
                Le BFR correspond au besoin de financement généré par le décalage entre les encaissements et les décaissements liés à l'activité :
                BFR = (Stocks + Créances clients et autres créances) - (Dettes fournisseurs et autres dettes circulantes)

                Le BFR est généralement positif : l'entreprise doit financer ses stocks et le crédit accordé à ses clients avant d'être payée. Certaines entreprises (grande distribution, par exemple) ont un BFR négatif : elles sont payées au comptant par leurs clients mais paient leurs fournisseurs à 60 ou 90 jours, ce qui constitue une ressource de financement.

                LA TRÉSORERIE NETTE (TN)
                TN = FR - BFR

                Cette relation est fondamentale :
                - Si FR > BFR → Trésorerie nette positive (l'entreprise dispose de liquidités)
                - Si FR < BFR → Trésorerie nette négative (l'entreprise doit recourir à des concours bancaires de court terme)

                LEVIERS POUR AMÉLIORER LA TRÉSORERIE
                1. Augmenter le FR : augmentation de capital, nouveaux emprunts à long terme, mise en réserve des bénéfices
                2. Diminuer le BFR : réduire les stocks (juste-à-temps), négocier des délais de paiement clients plus courts, négocier des délais fournisseurs plus longs

                Exemple :
                FR = 5 000 000 FCFA, BFR = 3 500 000 FCFA → Trésorerie nette = 1 500 000 FCFA (positive)
                Si le BFR augmente à 6 000 000 FCFA (par exemple à cause d'un gonflement des stocks), la trésorerie nette devient négative (-1 000 000 FCFA) et l'entreprise devra recourir à un découvert bancaire.

                Cette analyse dynamique complète l'analyse statique du bilan et prépare l'étude de cas de la prochaine leçon.
                """),
            new LessonSpec("Étude de cas : analyse financière d'une entreprise", Lesson.LessonType.PDF, 35, """
                ÉTUDE DE CAS : SARL "SENEGAL DISTRIBUTION"

                Vous disposez des informations suivantes extraites des états financiers de l'exercice N de la SARL Senegal Distribution (montants en milliers de FCFA) :

                BILAN (extraits) :
                - Actif immobilisé net : 45 000
                - Stocks : 18 000
                - Créances clients : 12 000
                - Trésorerie (banque + caisse) : 5 000
                - Capitaux propres : 40 000
                - Dettes financières long terme : 20 000
                - Dettes fournisseurs : 15 000
                - Autres dettes circulantes : 5 000

                COMPTE DE RÉSULTAT (extraits) :
                - Chiffre d'affaires : 120 000
                - Achats de marchandises consommées : 75 000
                - Charges de personnel : 18 000
                - Autres charges d'exploitation : 8 000
                - Dotations aux amortissements : 6 000
                - Charges financières : 2 000
                - Impôt sur les sociétés : 3 600

                TRAVAIL À FAIRE :

                1. Calculez le total du bilan (Total Actif = Total Passif).
                   Indice : Total Actif = 45 000 + 18 000 + 12 000 + 5 000 = 80 000

                2. Calculez le fonds de roulement (FR).
                   Indice : FR = Ressources stables - Emplois stables = (40 000 + 20 000) - 45 000 = 15 000

                3. Calculez le besoin en fonds de roulement (BFR).
                   Indice : BFR = (Stocks + Créances) - (Dettes fournisseurs + Autres dettes) = (18 000 + 12 000) - (15 000 + 5 000) = 10 000

                4. Calculez la trésorerie nette (TN) et vérifiez la cohérence avec la trésorerie figurant au bilan.
                   Indice : TN = FR - BFR = 15 000 - 10 000 = 5 000 (cohérent avec la trésorerie de 5 000 au bilan)

                5. Calculez le résultat net de l'exercice.
                   Indice : Résultat net = CA - Achats - Charges de personnel - Autres charges - Dotations - Charges financières - IS
                   = 120 000 - 75 000 - 18 000 - 8 000 - 6 000 - 2 000 - 3 600 = 7 400

                6. Calculez et commentez le ROE (Rentabilité financière) et le ratio d'autonomie financière.
                   Indice : ROE = Résultat net / Capitaux propres = 7 400 / 40 000 = 18,5 %
                   Ratio d'autonomie financière = Capitaux propres / Total Passif = 40 000 / 80 000 = 50 %

                ANALYSE ATTENDUE :
                Commentez la situation financière globale de l'entreprise : équilibre financier (FR > BFR), niveau de rentabilité (ROE), et structure de financement (autonomie financière). Proposez deux recommandations pour améliorer encore la performance de l'entreprise.
                """),
            new LessonSpec("Cas pratique : analyse du bilan et calcul de ratios (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 25, """
                Vous disposez ci-dessous du bilan simplifié et d'extraits du compte de résultat de l'entreprise DAKAR SA au 31 décembre N. Les données sont déjà saisies dans le tableur.

                Votre mission :
                1. Vérifiez que le Total Actif et le Total Passif s'équilibrent (les formules SOMME sont déjà en place).
                2. Calculez les 3 ratios financiers en cellules B15, B16 et B17 à l'aide des formules indiquées :
                   - Autonomie financière = Capitaux propres / Total Passif → doit être > 50 % pour indiquer une bonne indépendance
                   - Taux de marge nette = Résultat net / Chiffre d'affaires → exprime la profitabilité en %
                   - Liquidité générale = (Stocks + Créances + Trésorerie) / (Dettes fournisseurs + Autres dettes CT) → doit être > 1
                3. Interprétez chaque ratio dans la colonne G : bonne ou mauvaise situation ? Pourquoi ?

                Données de référence :
                - Total Actif = Total Passif = 11 000 000
                - Résultat net = 1 200 000, Chiffre d'affaires = 12 000 000
                - Capitaux propres = 6 000 000

                Résultats attendus : Autonomie financière ≈ 54,5 %, Taux de marge nette = 10 %, Liquidité générale ≈ 1,28
                """, """
                {"cells":{
                  "A1":{"raw":"BILAN SIMPLIFIÉ - ENTREPRISE DAKAR SA (31/12/N)","bold":true},
                  "A3":{"raw":"ACTIF","bold":true},
                  "A4":{"raw":"Actif immobilisé net"},
                  "B4":{"raw":"8500000","format":"currency"},
                  "A5":{"raw":"Stocks"},
                  "B5":{"raw":"1200000","format":"currency"},
                  "A6":{"raw":"Créances clients"},
                  "B6":{"raw":"980000","format":"currency"},
                  "A7":{"raw":"Trésorerie"},
                  "B7":{"raw":"320000","format":"currency"},
                  "A8":{"raw":"TOTAL ACTIF","bold":true},
                  "B8":{"raw":"=SOMME(B4:B7)","bold":true,"format":"currency"},
                  "D3":{"raw":"PASSIF","bold":true},
                  "D4":{"raw":"Capitaux propres"},
                  "E4":{"raw":"6000000","format":"currency"},
                  "D5":{"raw":"Dettes financières LT"},
                  "E5":{"raw":"2500000","format":"currency"},
                  "D6":{"raw":"Dettes fournisseurs"},
                  "E6":{"raw":"750000","format":"currency"},
                  "D7":{"raw":"Autres dettes CT"},
                  "E7":{"raw":"1750000","format":"currency"},
                  "D8":{"raw":"TOTAL PASSIF","bold":true},
                  "E8":{"raw":"=SOMME(E4:E7)","bold":true,"format":"currency"},
                  "A10":{"raw":"COMPTE DE RÉSULTAT - extraits","bold":true},
                  "A11":{"raw":"Chiffre d'affaires"},
                  "B11":{"raw":"12000000","format":"currency"},
                  "A12":{"raw":"Résultat net"},
                  "B12":{"raw":"1200000","format":"currency"},
                  "A14":{"raw":"RATIOS À CALCULER","bold":true},
                  "F14":{"raw":"Interprétation","bold":true},
                  "A15":{"raw":"Autonomie financière (CP / Total Passif)"},
                  "B15":{"raw":"=E4/E8","format":"percent"},
                  "A16":{"raw":"Taux de marge nette (RN / CA)"},
                  "B16":{"raw":"=B12/B11","format":"percent"},
                  "A17":{"raw":"Liquidité générale ((Stocks+Créances+Tréso) / Dettes CT)"},
                  "B17":{"raw":"=(B5+B6+B7)/(E6+E7)","format":"number"}
                }}
                """),
            new LessonSpec("Quiz : Gestion financière", Lesson.LessonType.QUIZ, 15, """
                Ce quiz porte sur l'ensemble du module "Gestion Financière et Analyse de Bilan". Il couvre :

                - La lecture et l'interprétation des trois états financiers principaux (bilan, compte de résultat, tableau de flux de trésorerie)
                - Le calcul et l'interprétation des principaux ratios : rentabilité (ROE, ROA, marge nette), liquidité, structure financière et délais de rotation
                - Les notions de fonds de roulement (FR), besoin en fonds de roulement (BFR) et trésorerie nette (TN), et leur relation TN = FR - BFR
                - L'application de ces notions à un cas concret d'analyse d'entreprise

                Avant de commencer, assurez-vous de bien maîtriser les formules suivantes :
                - FR = Ressources stables - Emplois stables
                - BFR = (Stocks + Créances) - Dettes circulantes
                - TN = FR - BFR
                - ROE = Résultat net / Capitaux propres

                Conseil : pour les questions de calcul, identifiez d'abord les données nécessaires dans l'énoncé, puis appliquez la formule appropriée étape par étape. N'hésitez pas à revoir l'étude de cas de la leçon précédente pour vous entraîner.

                Bon courage !
                """)
        );
    }

    private List<LessonSpec> comptaCours3Lessons() {
        return List.of(
            new LessonSpec("Introduction au système fiscal et impôt sur les sociétés", Lesson.LessonType.VIDEO, 25, """
                La fiscalité d'entreprise regroupe l'ensemble des impôts et taxes auxquels une entreprise est assujettie dans le cadre de son activité. Une bonne compréhension de ce système est indispensable pour assurer la conformité fiscale et optimiser la gestion financière de l'entreprise.

                LES PRINCIPAUX IMPÔTS D'ENTREPRISE
                - L'impôt sur les sociétés (IS) : impôt sur le bénéfice réalisé par les personnes morales (SA, SARL...)
                - La TVA (taxe sur la valeur ajoutée) : impôt sur la consommation, collecté par les entreprises pour le compte de l'État
                - Les impôts et taxes locaux (patente, contribution foncière...)
                - Les retenues à la source (sur salaires, sur prestations de services rendues par des non-résidents...)

                L'IMPÔT SUR LES SOCIÉTÉS (IS)
                L'IS est calculé sur le bénéfice fiscal de l'entreprise, qui n'est pas toujours identique au résultat comptable. Le calcul suit généralement le schéma suivant :

                Résultat comptable avant impôt
                   + Réintégrations fiscales (charges non déductibles : amendes, charges somptuaires, excédent d'amortissement...)
                   - Déductions fiscales (produits non imposables, plus-values exonérées sous conditions...)
                   = Résultat fiscal

                Le taux d'IS de droit commun est généralement de 30 % du résultat fiscal (taux applicable dans plusieurs pays de l'espace UEMOA, dont le Sénégal).

                L'IMPÔT MINIMUM FORFAITAIRE (IMF)
                Même en cas de déficit, les entreprises sont en principe redevables d'un impôt minimum forfaitaire, calculé sur le chiffre d'affaires (un taux minimal, par exemple 0,5 % du CA, avec un montant plancher). L'entreprise paie le plus élevé entre l'IS calculé sur le résultat fiscal et l'IMF.

                EXEMPLE SIMPLIFIÉ :
                Une entreprise réalise un résultat comptable avant impôt de 10 000 000 FCFA. Après réintégration de 1 000 000 FCFA de charges non déductibles, le résultat fiscal est de 11 000 000 FCFA.
                IS = 11 000 000 × 30 % = 3 300 000 FCFA

                Dans les prochaines leçons, nous étudierons la TVA, les charges sociales et le calendrier des obligations déclaratives.
                """),
            new LessonSpec("La TVA : déclaration et régularisation", Lesson.LessonType.PDF, 30, """
                La TVA (Taxe sur la Valeur Ajoutée) est un impôt indirect supporté in fine par le consommateur final, mais collecté et reversé à l'État par les entreprises à chaque étape du circuit économique.

                PRINCIPE DE FONCTIONNEMENT
                À chaque vente, l'entreprise facture la TVA à son client (TVA collectée). Sur ses achats, elle paie de la TVA à ses fournisseurs (TVA déductible). La différence entre les deux constitue la TVA à reverser à l'État :

                TVA due = TVA collectée (sur les ventes) - TVA déductible (sur les achats et investissements)

                - Si TVA collectée > TVA déductible → l'entreprise doit reverser la différence à l'État
                - Si TVA déductible > TVA collectée → l'entreprise dispose d'un crédit de TVA, reportable sur les déclarations suivantes ou remboursable sous conditions

                TAUX DE TVA
                Dans de nombreux pays de l'UEMOA, le taux normal de TVA est de 18 %. Certaines opérations sont exonérées (exportations, certains produits de première nécessité, opérations bancaires et financières...).

                EXEMPLE DE CALCUL
                Une entreprise réalise au cours du mois :
                - Ventes HT : 10 000 000 FCFA → TVA collectée = 10 000 000 × 18 % = 1 800 000 FCFA
                - Achats de biens et services HT : 6 000 000 FCFA → TVA déductible = 6 000 000 × 18 % = 1 080 000 FCFA

                TVA due = 1 800 000 - 1 080 000 = 720 000 FCFA à reverser au Trésor public.

                LA RÉGULARISATION DE TVA
                La déduction de la TVA sur certains biens (notamment les immobilisations) peut être remise en cause si le bien change d'affectation (par exemple, un bien utilisé pour une activité exonérée après avoir été affecté à une activité taxable). On parle alors de régularisation de TVA : l'entreprise doit reverser une partie de la TVA initialement déduite, calculée au prorata de la durée d'utilisation restante (généralement sur une période de 5 ans pour les biens meubles et 20 ans pour les immeubles).

                ÉCRITURE COMPTABLE TYPE (déclaration mensuelle) :
                Débit  4431 État, TVA collectée         1 800 000
                Crédit 4452 État, TVA déductible                  1 080 000
                Crédit 4441 État, TVA à décaisser                   720 000

                La déclaration de TVA doit être déposée et le montant payé dans les délais fixés par l'administration fiscale (généralement avant le 15 du mois suivant).
                """),
            new LessonSpec("Charges sociales et déclarations CNSS/IPRES", Lesson.LessonType.PDF, 25, """
                Au-delà de la fiscalité, l'entreprise employeur doit s'acquitter de cotisations sociales destinées à financer la protection sociale de ses salariés : retraite, prestations familiales, accidents du travail, maladie...

                LES ORGANISMES SOCIAUX (cas du Sénégal, à titre d'exemple représentatif de la sous-région)
                - CSS (Caisse de Sécurité Sociale) : prestations familiales, accidents du travail et maladies professionnelles
                - IPRES (Institut de Prévoyance Retraite du Sénégal) : régime de retraite (régime général et régime cadre)

                PRINCIPE DE CALCUL DES COTISATIONS
                Les cotisations sociales sont calculées en appliquant un taux au salaire brut (ou à une assiette plafonnée selon le risque), et se répartissent entre :
                - La part salariale (retenue sur le salaire brut du salarié)
                - La part patronale (charge supplémentaire supportée par l'employeur, en plus du salaire brut)

                EXEMPLE ILLUSTRATIF (taux à titre pédagogique) :
                Pour un salarié au salaire brut de 500 000 FCFA :
                - Cotisation retraite (IPRES) : part salariale 5,6 % = 28 000 FCFA ; part patronale 8,4 % = 42 000 FCFA
                - Prestations familiales (CSS) : part patronale 7 % = 35 000 FCFA (entièrement à la charge de l'employeur)
                - Accidents du travail : part patronale 1 à 3 % selon le secteur

                Le salaire net à payer au salarié = Salaire brut - Cotisations salariales - Impôt sur le revenu (retenue à la source)
                Le coût total employeur = Salaire brut + Cotisations patronales

                DÉCLARATIONS SOCIALES
                L'employeur doit déposer périodiquement (généralement chaque trimestre ou chaque mois selon la taille de l'entreprise) une déclaration nominative des salaires (DNS) auprès des caisses sociales, accompagnée du paiement des cotisations dues. Le non-respect de ces obligations expose l'entreprise à des pénalités de retard et à des majorations.

                ÉCRITURE COMPTABLE TYPE (simplifiée) :
                Lors de la comptabilisation de la paie :
                Débit  661 Salaires et appointements (brut)
                Débit  664 Charges sociales (part patronale)
                Crédit 421 Personnel, rémunérations dues (net à payer)
                Crédit 431 Sécurité sociale (cotisations salariales + patronales)
                Crédit 447 État, impôts retenus à la source

                La rigueur dans la tenue de ces déclarations est essentielle : elle protège les droits sociaux des salariés (retraite, prestations familiales) et évite à l'entreprise des redressements coûteux.
                """),
            new LessonSpec("Calendrier fiscal et obligations déclaratives", Lesson.LessonType.VIDEO, 20, """
                La bonne gestion fiscale d'une entreprise repose sur le respect d'un calendrier précis d'obligations déclaratives et de paiements. Un retard, même involontaire, entraîne généralement des pénalités et intérêts de retard.

                PRINCIPALES OBLIGATIONS RÉCURRENTES (calendrier type, à adapter selon la législation locale) :

                CHAQUE MOIS (avant le 15 ou le 20 du mois suivant selon les pays) :
                - Déclaration et paiement de la TVA
                - Versement des retenues à la source sur salaires (IRPP / impôt sur le revenu)
                - Versement des cotisations sociales (CSS, IPRES) selon la périodicité applicable

                CHAQUE TRIMESTRE :
                - Acomptes provisionnels sur l'impôt sur les sociétés (IS), généralement calculés sur la base de l'IS de l'exercice précédent
                - Déclaration des salaires auprès des organismes sociaux (pour certaines entreprises)

                UNE FOIS PAR AN :
                - Déclaration annuelle des résultats (déclaration fiscale de l'IS), avec dépôt des états financiers (bilan, compte de résultat, annexes) généralement avant le 30 avril ou le 30 juin selon la clôture de l'exercice
                - Paiement du solde de liquidation de l'IS (différence entre l'IS dû et les acomptes déjà versés)
                - Déclaration et paiement de la patente / contribution des patentes
                - Déclaration des contributions foncières (pour les entreprises propriétaires d'immeubles)
                - Déclaration récapitulative annuelle des salaires (état 301 ou équivalent)

                BONNES PRATIQUES POUR LE SUIVI FISCAL
                1. Tenir un agenda fiscal partagé entre la direction financière et le service comptable, avec rappels avant chaque échéance
                2. Provisionner mensuellement les charges fiscales et sociales pour éviter les tensions de trésorerie au moment du paiement
                3. Conserver une traçabilité complète des déclarations déposées (accusés de réception, preuves de paiement)
                4. Anticiper les contrôles fiscaux en tenant une comptabilité probante et des pièces justificatives classées

                CONSÉQUENCES DU NON-RESPECT DES DÉLAIS
                - Pénalités de retard (souvent un pourcentage fixe du montant dû)
                - Intérêts de retard (calculés au prorata du temps écoulé)
                - Majoration en cas de récidive ou de mauvaise foi
                - Risque de contrôle fiscal accru pour les entreprises présentant des retards récurrents

                Une gestion fiscale rigoureuse et anticipée est un facteur de pérennité pour l'entreprise et un gage de confiance auprès des partenaires (banques, investisseurs, administration).
                """),
            new LessonSpec("Cas pratique : déclaration mensuelle de TVA (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 20, """
                Vous devez établir la déclaration de TVA du mois de janvier pour l'entreprise SÉNÉGAL IMPORT-EXPORT SARL, assujettie à la TVA au taux normal de 18 %.

                Données du mois de janvier :
                - Ventes HT soumises à TVA : 5 000 000 FCFA
                - Achats HT soumis à TVA : 2 800 000 FCFA (fournisseurs locaux assujettis)
                - Achats exonérés de TVA : 400 000 FCFA (ne génèrent pas de TVA déductible)

                Votre mission :
                1. Le tableur calcule automatiquement la TVA collectée (B5) et la TVA déductible (B9). Vérifiez les formules.
                2. La cellule B12 donne le montant de TVA nette due (si positif → à verser à la DGI ; si négatif → crédit de TVA).
                3. Saisissez en B15 le montant des achats exonérés (400 000) et observez qu'il n'y a pas de TVA déductible sur ce poste.
                4. En cellule D5 et D9, saisissez un commentaire : "Collectée auprès des clients" et "Payée aux fournisseurs".
                5. Quel serait le montant de TVA due si le chiffre d'affaires HT passait à 8 000 000 ? Modifiez B4 pour tester.

                Résultat attendu (données initiales) :
                TVA collectée = 900 000 FCFA | TVA déductible = 504 000 FCFA | TVA nette due = 396 000 FCFA
                """, """
                {"cells":{
                  "A1":{"raw":"DÉCLARATION TVA MENSUELLE - JANVIER","bold":true},
                  "A3":{"raw":"TVA COLLECTÉE (sur les ventes)","bold":true},
                  "A4":{"raw":"Ventes HT soumises à TVA 18%"},
                  "B4":{"raw":"5000000","format":"currency"},
                  "A5":{"raw":"TVA collectée (B4 × 18%)","bold":true},
                  "B5":{"raw":"=B4*0.18","bold":true,"format":"currency"},
                  "A7":{"raw":"TVA DÉDUCTIBLE (sur les achats)","bold":true},
                  "A8":{"raw":"Achats HT soumis à TVA 18%"},
                  "B8":{"raw":"2800000","format":"currency"},
                  "A9":{"raw":"TVA déductible (B8 × 18%)","bold":true},
                  "B9":{"raw":"=B8*0.18","bold":true,"format":"currency"},
                  "A11":{"raw":"RÉSULTAT DE LA DÉCLARATION","bold":true},
                  "A12":{"raw":"TVA nette due  (TVA collectée − TVA déductible)","bold":true},
                  "B12":{"raw":"=B5-B9","bold":true,"format":"currency"},
                  "A13":{"raw":"(valeur négative = crédit de TVA à reporter)"},
                  "A15":{"raw":"Achats HT exonérés (sans TVA déductible)"},
                  "B15":{"raw":"400000","format":"currency"}
                }}
                """),
            new LessonSpec("Quiz : Fiscalité d'entreprise", Lesson.LessonType.QUIZ, 15, """
                Ce quiz récapitule les notions essentielles du module "Fiscalité d'Entreprise et Déclarations" :

                - Le mécanisme de l'impôt sur les sociétés (IS) : du résultat comptable au résultat fiscal, réintégrations et déductions, taux applicable
                - Le fonctionnement de la TVA : TVA collectée, TVA déductible, TVA due ou crédit de TVA, et les cas de régularisation
                - Les cotisations sociales (CSS/IPRES) : part salariale vs part patronale, calcul du salaire net et du coût employeur
                - Le calendrier fiscal : obligations mensuelles, trimestrielles et annuelles, et les conséquences du non-respect des délais

                Avant de répondre, vérifiez que vous savez :
                - Calculer une TVA due à partir de la TVA collectée et de la TVA déductible
                - Distinguer une charge déductible d'une charge à réintégrer pour le calcul de l'IS
                - Identifier les principales échéances fiscales et sociales d'une entreprise

                Conseil : relisez les exemples chiffrés des leçons précédentes, ils reprennent les mécanismes de calcul qui pourront être testés dans ce quiz.

                Bon courage !
                """)
        );
    }

    // ===================== Sage-Femme d'État =====================

    private List<LessonSpec> sageFemmeCours1Lessons() {
        return List.of(
            new LessonSpec("Anatomie de l'appareil reproducteur féminin", Lesson.LessonType.VIDEO, 25, """
                La compréhension de l'anatomie de l'appareil reproducteur féminin est le socle indispensable de la pratique de la sage-femme. Cet appareil comprend des organes internes et externes, dont le fonctionnement coordonné permet la reproduction.

                LES ORGANES GÉNITAUX EXTERNES (VULVE)
                - Le mont du pubis, les grandes lèvres et les petites lèvres : structures de protection
                - Le clitoris : organe érectile sensitif
                - Le vestibule vulvaire : zone où débouchent l'urètre et le vagin
                - Le périnée : ensemble musculo-aponévrotique qui ferme le bassin et soutient les organes pelviens ; sa connaissance est essentielle pour la prévention des déchirures lors de l'accouchement

                LES ORGANES GÉNITAUX INTERNES
                - Le vagin : conduit musculo-membraneux reliant la vulve au col de l'utérus, voie de passage lors de l'accouchement
                - L'utérus : organe musculaire creux, divisé en corps utérin (cavité où se développe la grossesse) et col utérin (qui s'efface et se dilate pendant le travail). La paroi utérine comprend trois couches : périmètre (séreuse), myomètre (muscle, responsable des contractions) et endomètre (muqueuse, qui devient la caduque pendant la grossesse)
                - Les trompes de Fallope (ou trompes utérines) : conduits reliant les ovaires à l'utérus, lieu habituel de la fécondation
                - Les ovaires : glandes sexuelles produisant les ovocytes et les hormones (œstrogènes, progestérone)

                LE BASSIN OSSEUX (PELVIS)
                Le bassin obstétrical est formé par les deux os iliaques, le sacrum et le coccyx. Sa morphologie (forme gynécoïde, androïde, anthropoïde ou platypelloïde) et ses dimensions (détroit supérieur, excavation pelvienne, détroit inférieur) conditionnent le déroulement mécanique de l'accouchement. La sage-femme doit savoir évaluer cliniquement le bassin (pelvimétrie clinique) pour anticiper d'éventuelles difficultés.

                LE CYCLE MENSTRUEL
                Le cycle ovarien (folliculaire, ovulation, lutéal) est synchronisé avec le cycle utérin (phases proliférative, sécrétoire, menstruelle) sous le contrôle de l'axe hypothalamo-hypophyso-ovarien (FSH, LH, œstrogènes, progestérone). Cette compréhension hormonale est indispensable pour appréhender les mécanismes de la fécondation et de la nidation, qui seront abordés dans la leçon suivante sur la physiologie de la grossesse.
                """),
            new LessonSpec("Physiologie de la grossesse et changements maternels", Lesson.LessonType.PDF, 30, """
                La grossesse entraîne d'importantes adaptations physiologiques chez la femme, touchant pratiquement tous les systèmes de l'organisme. La sage-femme doit connaître ces modifications pour distinguer ce qui relève de l'adaptation normale de ce qui constitue un signe d'alerte.

                MODIFICATIONS DE L'APPAREIL GÉNITAL
                - L'utérus passe d'environ 50 g à 1000 g en fin de grossesse, et son volume augmente considérablement sous l'effet de l'hypertrophie et de l'hyperplasie des fibres musculaires (myomètre)
                - Le col utérin se ramollit (signe de Hegar) et devient cyanosé (signe de Chadwick) sous l'effet de la vascularisation accrue
                - Le vagin devient plus souple et plus vascularisé, préparant le passage du fœtus

                MODIFICATIONS CARDIOVASCULAIRES
                - Le volume sanguin circulant augmente de 30 à 50 %, principalement par augmentation du volume plasmatique
                - Le débit cardiaque augmente de 30 à 50 %, par augmentation de la fréquence cardiaque et du volume d'éjection systolique
                - La pression artérielle a tendance à diminuer légèrement au 2e trimestre avant de revenir à la normale en fin de grossesse
                - Une anémie physiologique de dilution peut apparaître (hémodilution)

                MODIFICATIONS RESPIRATOIRES
                - Le volume courant augmente, entraînant une légère hyperventilation et une sensation de dyspnée chez certaines femmes
                - Le diaphragme est progressivement repoussé vers le haut par l'utérus gravide

                MODIFICATIONS DIGESTIVES ET URINAIRES
                - Ralentissement du transit intestinal (sous l'effet de la progestérone), favorisant la constipation
                - Reflux gastro-œsophagien fréquent par relâchement du sphincter œsophagien inférieur
                - Augmentation de la filtration glomérulaire rénale, pollakiurie (envies fréquentes d'uriner)

                MODIFICATIONS ENDOCRINIENNES
                - Le placenta devient une véritable glande endocrine, sécrétant notamment l'hCG (hormone chorionique gonadotrope, à la base des tests de grossesse), les œstrogènes et la progestérone, ainsi que l'hormone lactogène placentaire (hPL)
                - Ces hormones assurent le maintien de la grossesse, préparent les glandes mammaires à la lactation et modulent le métabolisme maternel pour répondre aux besoins du fœtus

                PRISE DE POIDS
                La prise de poids moyenne recommandée pendant la grossesse est de l'ordre de 9 à 12 kg pour une femme de poids normal avant la grossesse, répartie entre le fœtus, le placenta, le liquide amniotique, l'utérus, les seins, le volume sanguin et les réserves maternelles.

                Toutes ces adaptations, normales et nécessaires au bon déroulement de la grossesse, doivent néanmoins être surveillées : une prise de poids excessive, une hypertension ou un œdème important peuvent signaler une complication (pré-éclampsie), qui sera abordée dans le module suivant sur le suivi prénatal.
                """),
            new LessonSpec("Développement embryonnaire et fœtal", Lesson.LessonType.VIDEO, 30, """
                Le développement de l'œuf humain, de la fécondation à la naissance, se déroule en trois grandes périodes : la période pré-embryonnaire, la période embryonnaire et la période fœtale.

                LA FÉCONDATION ET LA NIDATION (semaines 1-2)
                La fécondation a lieu dans le tiers externe de la trompe de Fallope, lorsqu'un spermatozoïde pénètre l'ovocyte. L'œuf fécondé (zygote) se divise rapidement pendant son trajet vers l'utérus (morula, puis blastocyste) et s'implante dans l'endomètre vers le 6e-7e jour après la fécondation : c'est la nidation.

                LA PÉRIODE EMBRYONNAIRE (semaines 3 à 8)
                C'est la période de l'organogenèse : la mise en place de tous les organes principaux.
                - Semaine 3-4 : formation des trois feuillets embryonnaires (ectoderme, mésoderme, endoderme) ; ébauche du système nerveux (tube neural) et du cœur, qui commence à battre vers le 22e jour
                - Semaine 5-6 : développement des bourgeons des membres, des yeux, des oreilles
                - Semaine 7-8 : différenciation des principaux organes internes (foie, reins, poumons en formation)

                Cette période est la plus sensible aux agents tératogènes (médicaments, infections, alcool, tabac) car c'est durant cette phase que se forment les organes.

                LA PÉRIODE FŒTALE (semaine 9 à la naissance)
                - 1er trimestre (jusqu'à 12-14 SA) : poursuite de la maturation des organes, apparition des mouvements actifs (non perçus par la mère), formation des organes génitaux externes
                - 2e trimestre (14-28 SA) : croissance rapide, perception des mouvements actifs par la mère (vers 18-20 SA), développement du système nerveux central, apparition du lanugo (fin duvet)
                - 3e trimestre (28-42 SA) : maturation pulmonaire (production de surfactant, essentielle pour la respiration à la naissance), prise de poids importante, mise en place de la présentation (le plus souvent céphalique vers la fin de la grossesse)

                LE PLACENTA, LE CORDON OMBILICAL ET LE LIQUIDE AMNIOTIQUE
                - Le placenta assure les échanges materno-fœtaux (oxygène, nutriments, déchets) et la fonction endocrine de la grossesse
                - Le cordon ombilical relie le fœtus au placenta : il contient deux artères ombilicales et une veine ombilicale
                - Le liquide amniotique protège le fœtus, lui permet de bouger, et participe au développement pulmonaire (le fœtus "respire" du liquide amniotique)

                REPÈRES POUR LA PRATIQUE
                La sage-femme utilise l'âge gestationnel (calculé en semaines d'aménorrhée, SA, à partir du premier jour des dernières règles) pour situer le développement fœtal et planifier les examens prénataux adaptés à chaque étape, qui seront détaillés dans la prochaine leçon.
                """),
            new LessonSpec("Les examens prénataux essentiels", Lesson.LessonType.PDF, 25, """
                Le suivi prénatal repose sur un calendrier d'examens cliniques, biologiques et échographiques permettant de surveiller le bon déroulement de la grossesse et de dépister précocement les situations à risque.

                EXAMENS CLINIQUES À CHAQUE CONSULTATION
                - Mesure du poids et de la pression artérielle (dépistage de la pré-éclampsie)
                - Mesure de la hauteur utérine (suivi de la croissance fœtale)
                - Recherche des bruits du cœur fœtal (à partir d'environ 10-12 SA au Doppler, 18-20 SA au stéthoscope obstétrical)
                - Recherche d'œdèmes, signes fonctionnels (céphalées, troubles visuels, douleurs abdominales)
                - Recherche de la présentation fœtale (palpation abdominale) en fin de grossesse

                EXAMENS BIOLOGIQUES
                - Groupe sanguin et rhésus (avec recherche d'agglutinines irrégulières si la mère est rhésus négatif)
                - Numération formule sanguine (dépistage de l'anémie)
                - Glycémie (dépistage du diabète gestationnel, notamment entre 24 et 28 SA par hyperglycémie provoquée par voie orale - HGPO)
                - Sérologies : toxoplasmose, rubéole, syphilis, VIH (avec accord de la patiente), hépatite B
                - Recherche de protéinurie et glycosurie à la bandelette urinaire à chaque consultation
                - Dépistage de la drépanocytose et autres pathologies selon le contexte épidémiologique local

                EXAMENS ÉCHOGRAPHIQUES (3 échographies de référence)
                - Échographie du 1er trimestre (11-13 SA + 6 jours) : confirmation de la grossesse, datation précise, dépistage de certaines anomalies (mesure de la clarté nucale), diagnostic de grossesse multiple
                - Échographie du 2e trimestre (20-22 SA) : étude morphologique détaillée du fœtus, contrôle de la croissance, localisation du placenta
                - Échographie du 3e trimestre (30-32 SA) : évaluation de la croissance fœtale, de la quantité de liquide amniotique, de la présentation et de la position du placenta (dépistage du placenta prævia)

                CALENDRIER DES CONSULTATIONS PRÉNATALES (CPN)
                L'Organisation Mondiale de la Santé recommande au moins 8 contacts prénatals au cours de la grossesse, répartis sur les trois trimestres, afin de détecter précocement les complications, de fournir des conseils nutritionnels et de préparer la femme à l'accouchement et à l'allaitement.

                VACCINATIONS ET SUPPLÉMENTATIONS
                - Vaccination antitétanique (VAT) selon le statut vaccinal antérieur
                - Supplémentation systématique en fer et en acide folique
                - Supplémentation en sulfate ferreux et déparasitage selon les protocoles nationaux

                L'ensemble de ces examens permet à la sage-femme de classer la grossesse comme à bas risque ou à risque, et d'orienter si nécessaire vers une consultation spécialisée. Ces principes seront approfondis dans le module suivant consacré au suivi prénatal et à l'accouchement.
                """),
            new LessonSpec("Cas pratique : grille de surveillance de la grossesse (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 20, """
                Cas clinique : Madame FALL, 28 ans, primigeste, consulte pour son suivi de grossesse. Sa DPA (date présumée d'accouchement) est dans 24 semaines. Vous devez compléter sa fiche de surveillance.

                Votre mission :
                1. Remplissez les données de chaque consultation prénatale dans les lignes 4 à 9 :
                   - 16 SA : poids 58 kg, TA 115/70, HU 14 cm, BCF 148 bpm
                   - 20 SA : poids 60 kg, TA 120/75, HU 18 cm, BCF 152 bpm
                   - 24 SA : poids 62,5 kg, TA 118/72, HU 22 cm, BCF 144 bpm
                   - 28 SA : poids 64 kg, TA 130/85 (à surveiller !), HU 26 cm, BCF 156 bpm
                   - 32 SA : poids 66,5 kg, TA 128/82, HU 30 cm, BCF 148 bpm
                   - 36 SA : poids 68 kg, TA 125/78, HU 34 cm, BCF 142 bpm
                2. La cellule C11 calcule automatiquement la prise de poids totale (poids à 36 SA − poids à 16 SA). Est-elle dans la norme (8-14 kg en général) ?
                3. Dans la colonne G, saisissez vos observations cliniques pour chaque consultation (normal, surveillance, alerte).
                4. Identifiez la consultation où la TA diastolique dépasse 80 mmHg et signalez-la en rouge (mettre "ALERTE HTA" dans la colonne G).

                Norme HU attendue : SA − 4 (ex. : à 28 SA, HU ≈ 24 cm). Comparez les valeurs saisies.
                """, """
                {"cells":{
                  "A1":{"raw":"FICHE DE SURVEILLANCE DE GROSSESSE","bold":true},
                  "A2":{"raw":"Patiente : Mme FALL — DPA : dans 24 semaines"},
                  "A3":{"raw":"SA","bold":true},
                  "B3":{"raw":"Date visite","bold":true},
                  "C3":{"raw":"Poids (kg)","bold":true},
                  "D3":{"raw":"TA (mmHg)","bold":true},
                  "E3":{"raw":"HU (cm)","bold":true},
                  "F3":{"raw":"BCF (bpm)","bold":true},
                  "G3":{"raw":"Observations","bold":true},
                  "A4":{"raw":"16"},
                  "A5":{"raw":"20"},
                  "A6":{"raw":"24"},
                  "A7":{"raw":"28"},
                  "A8":{"raw":"32"},
                  "A9":{"raw":"36"},
                  "A11":{"raw":"Prise de poids totale (kg)","bold":true},
                  "C11":{"raw":"=C9-C4","bold":true,"format":"number"}
                }}
                """),
            new LessonSpec("Quiz : Anatomie et physiologie", Lesson.LessonType.QUIZ, 15, """
                Ce quiz porte sur les notions fondamentales du module "Anatomie et Physiologie de la Grossesse" :

                - L'anatomie de l'appareil reproducteur féminin : organes externes (vulve, périnée) et internes (vagin, utérus, trompes, ovaires), ainsi que le bassin obstétrical
                - Les principales adaptations physiologiques maternelles pendant la grossesse (cardiovasculaires, respiratoires, digestives, endocriniennes)
                - Les grandes étapes du développement embryonnaire et fœtal, des trois feuillets embryonnaires à la maturation pulmonaire du troisième trimestre
                - Le calendrier et le contenu des examens prénataux essentiels (cliniques, biologiques, échographiques)

                Avant de répondre, assurez-vous de pouvoir :
                - Nommer et situer les principaux organes de l'appareil reproducteur féminin
                - Expliquer pourquoi le volume sanguin et le débit cardiaque augmentent pendant la grossesse
                - Associer chaque trimestre de la grossesse aux principales étapes du développement fœtal
                - Citer les trois échographies de référence et leur objectif principal

                Conseil : revoyez en particulier la leçon "Développement embryonnaire et fœtal" qui présente une chronologie utile pour situer les autres notions du module.

                Bon courage !
                """)
        );
    }

    private List<LessonSpec> sageFemmeCours2Lessons() {
        return List.of(
            new LessonSpec("Consultations prénatales : protocole et surveillance", Lesson.LessonType.VIDEO, 30, """
                La consultation prénatale (CPN) est le pilier de la surveillance de la grossesse. Elle permet de suivre l'évolution normale de la grossesse, de dépister précocement les anomalies et de préparer la mère à l'accouchement.

                LE MODÈLE DE CONTACT PRÉNATAL OMS (8 CONTACTS)
                Le modèle recommandé par l'OMS prévoit :
                - 1 contact au 1er trimestre (avant 12 SA)
                - 2 contacts au 2e trimestre (vers 20 SA et 26 SA)
                - 5 contacts au 3e trimestre (vers 30, 34, 36, 38 et 40 SA)

                Ce calendrier renforcé permet d'augmenter les chances de détecter précocement les complications (pré-éclampsie, retard de croissance, présentations dystociques) et d'améliorer la communication avec la femme enceinte.

                CONTENU TYPE D'UNE CONSULTATION PRÉNATALE
                1. Interrogatoire : antécédents médicaux, obstétricaux, gynécologiques ; recherche de signes fonctionnels (saignements, contractions, perte de liquide, mouvements actifs fœtaux)
                2. Examen général : poids, taille, IMC, pression artérielle, recherche d'œdèmes et de pâleur (anémie)
                3. Examen obstétrical : mesure de la hauteur utérine, palpation abdominale (présentation, position), recherche des bruits du cœur fœtal
                4. Examens complémentaires selon le terme : bandelette urinaire, bilans biologiques, échographies
                5. Conseils et éducation : nutrition, hygiène, signes de danger devant amener à consulter en urgence, planification de l'accouchement (lieu, accompagnement)
                6. Prescriptions : supplémentation en fer/acide folique, vaccination antitétanique, traitement préventif intermittent du paludisme si applicable

                CLASSIFICATION DU RISQUE OBSTÉTRICAL
                À chaque consultation, la sage-femme évalue si la grossesse reste "à bas risque" (suivi standard) ou présente des facteurs de risque nécessitant une orientation vers un niveau de soins supérieur : antécédents de césarienne, grossesse multiple, hypertension, diabète, anomalies de présentation, antécédents d'accouchement difficile, etc.

                SIGNES DE DANGER À ENSEIGNER À LA FEMME ENCEINTE
                - Saignements vaginaux
                - Maux de tête sévères, troubles visuels
                - Douleurs abdominales intenses
                - Fièvre
                - Diminution ou absence des mouvements fœtaux
                - Perte de liquide amniotique
                - Œdèmes importants du visage et des mains

                La qualité et la régularité des consultations prénatales sont directement corrélées à la réduction de la mortalité maternelle et néonatale. La leçon suivante aborde les signes du travail et les différentes phases de l'accouchement.
                """),
            new LessonSpec("Signes du travail et phases de l'accouchement", Lesson.LessonType.PDF, 30, """
                Reconnaître le début du travail et en suivre l'évolution est une compétence centrale de la pratique de la sage-femme. Le travail obstétrical se définit comme l'ensemble des phénomènes qui conduisent à l'expulsion du fœtus et des annexes hors des voies génitales maternelles.

                SIGNES ANNONÇANT LE TRAVAIL
                - Contractions utérines régulières, progressivement plus rapprochées, plus longues et plus intenses (à différencier des contractions de Braxton-Hicks, irrégulières et indolores)
                - Modifications du col utérin : effacement (raccourcissement) et dilatation
                - Perte du bouchon muqueux (pertes glaireuses parfois teintées de sang)
                - Rupture de la poche des eaux (écoulement de liquide amniotique), spontanée ou artificielle

                LES TROIS PHASES DE L'ACCOUCHEMENT

                1. PREMIER STADE : LA DILATATION
                Cette phase va du début du travail jusqu'à la dilatation complète du col (10 cm). On distingue :
                - La phase de latence : dilatation de 0 à environ 5-6 cm, contractions encore espacées
                - La phase active : dilatation de 5-6 cm à 10 cm, contractions plus rapprochées et intenses, progression plus rapide
                La surveillance repose sur le partogramme : suivi graphique de la dilatation cervicale, de la descente de la présentation, des contractions, du rythme cardiaque fœtal et des constantes maternelles.

                2. DEUXIÈME STADE : L'EXPULSION
                Après dilatation complète, la phase d'expulsion correspond à la descente, la rotation et la sortie du fœtus à travers la filière génitale, jusqu'à la naissance complète. Elle est marquée par l'envie de pousser de la mère, synchronisée avec les contractions.

                3. TROISIÈME STADE : LA DÉLIVRANCE
                Cette phase correspond au décollement et à l'expulsion du placenta et des membranes, généralement dans les 30 minutes suivant la naissance. La surveillance de l'hémorragie du post-partum immédiat est cruciale durant cette phase (la délivrance dirigée, avec administration d'ocytocine, est recommandée pour prévenir l'hémorragie).

                SURVEILLANCE PENDANT LE TRAVAIL
                - Rythme cardiaque fœtal (auscultation régulière ou monitorage continu selon le contexte)
                - Caractéristiques des contractions (fréquence, durée, intensité)
                - Constantes maternelles (pouls, tension artérielle, température)
                - Progression de la dilatation et de la descente de la présentation
                - Couleur du liquide amniotique en cas de rupture des membranes (un liquide teinté de méconium peut être un signe de souffrance fœtale)

                La maîtrise de ces signes et de ces stades permet à la sage-femme d'anticiper le déroulement normal de l'accouchement et d'identifier rapidement toute déviation par rapport à la normale, nécessitant une prise en charge adaptée.
                """),
            new LessonSpec("Techniques d'accouchement eutocique", Lesson.LessonType.VIDEO, 35, """
                On parle d'accouchement eutocique lorsque le travail et l'expulsion se déroulent de façon normale, par voie basse, sans intervention instrumentale ni complication majeure, pour une présentation céphalique du sommet.

                PRÉPARATION DE L'ACCOUCHEMENT
                - Installation de la parturiente en position confortable (de nombreuses positions sont possibles : décubitus dorsal semi-assis, position semi-gynécologique, position accroupie ou latérale selon les pratiques et préférences)
                - Préparation du matériel stérile : champs, gants, instruments de section et de clampage du cordon, matériel d'aspiration et de réanimation néonatale
                - Vérification de la vacuité vésicale (la patiente est encouragée à uriner régulièrement)

                ACCOMPAGNEMENT DE LA PHASE D'EXPULSION
                - Encourager la parturiente à pousser de manière efficace, en synchronisation avec les contractions, en expirant pendant l'effort
                - Surveiller la progression de la présentation à chaque effort expulsif
                - Protéger le périnée : la sage-femme accompagne la sortie de la tête fœtale de manière progressive et contrôlée pour limiter le risque de déchirure périnéale, en pratiquant si besoin une épisiotomie selon les indications et les protocoles en vigueur

                MÉCANIQUE OBSTÉTRICALE DE LA PRÉSENTATION DU SOMMET
                La descente du fœtus suit une séquence de mouvements caractéristiques :
                1. Engagement de la tête fœtale dans le bassin
                2. Descente et rotation intra-pelvienne
                3. Dégagement de la tête par un mouvement de déflexion (extension)
                4. Rotation de restitution de la tête
                5. Dégagement des épaules (antérieure puis postérieure)
                6. Expulsion complète du corps

                ACCUEIL DU NOUVEAU-NÉ
                À la naissance :
                - Le nouveau-né est séché immédiatement et placé en peau à peau avec sa mère (sauf contre-indication)
                - Le score d'Apgar est évalué à 1 et 5 minutes (couleur, fréquence cardiaque, réflexes, tonus musculaire, respiration)
                - Le cordon ombilical est clampé puis sectionné (le clampage tardif, après l'arrêt des pulsations, est aujourd'hui recommandé en l'absence de complication)
                - Mise au sein précoce encouragée dans la première heure de vie

                LA DÉLIVRANCE DIRIGÉE
                Pour prévenir l'hémorragie du post-partum, la délivrance dirigée comprend :
                - L'administration d'ocytocine immédiatement après la naissance
                - La traction contrôlée du cordon associée à une contre-pression utérine
                - Le massage utérin après l'expulsion du placenta pour favoriser la rétraction utérine

                EXAMEN POST-ACCOUCHEMENT
                - Vérification de l'intégrité du placenta et des membranes
                - Inspection et, si nécessaire, suture des déchirures périnéales
                - Surveillance rapprochée de la mère pendant les deux heures suivant l'accouchement (surveillance du globe utérin, des saignements, des constantes)

                La maîtrise rigoureuse de ces techniques, associée à une surveillance attentive, permet de réduire significativement les complications maternelles et néonatales liées à l'accouchement.
                """),
            new LessonSpec("Gestion de la douleur et accompagnement de la parturiente", Lesson.LessonType.PDF, 25, """
                La douleur de l'accouchement est une expérience physiologique intense, dont la gestion adéquate contribue au bien-être de la mère, à la qualité du vécu de la naissance et, dans certains cas, à la fluidité du travail.

                ORIGINE DE LA DOULEUR DU TRAVAIL
                - Au premier stade : douleur principalement liée aux contractions utérines et à la dilatation cervicale (douleur viscérale, souvent ressentie dans le bas du dos et l'abdomen)
                - Au deuxième stade : douleur liée à la distension des tissus périnéaux et vaginaux lors du passage du fœtus (douleur somatique, plus localisée)

                MÉTHODES NON PHARMACOLOGIQUES
                - Techniques de respiration et de relaxation : respiration lente et contrôlée pendant les contractions, techniques de relaxation musculaire
                - Mobilité et changements de position : la mobilisation pendant le travail (marche, ballon de naissance, positions verticales) peut favoriser la descente fœtale et atténuer la perception de la douleur
                - Soutien continu : la présence d'un accompagnant (partenaire, doula, sage-femme) réduit l'anxiété et la perception douloureuse, et est associée à une meilleure satisfaction maternelle
                - Hydrothérapie : bains ou douches chaudes pendant le travail
                - Massage et contre-pression, notamment au niveau lombaire
                - Musicothérapie et techniques de distraction

                MÉTHODES PHARMACOLOGIQUES
                - Analgésie péridurale : technique la plus efficace pour soulager la douleur du travail, nécessitant la présence d'un anesthésiste et une surveillance spécifique
                - Antalgiques systémiques (selon les protocoles locaux) : utilisés lorsque la péridurale n'est pas disponible ou non souhaitée
                - Anesthésie locale du périnée en cas de suture (épisiotomie ou déchirure)

                ACCOMPAGNEMENT GLOBAL DE LA PARTURIENTE
                - Information claire sur le déroulement du travail et les options disponibles pour la gestion de la douleur, idéalement dès les consultations prénatales
                - Respect du choix de la femme et de son projet de naissance, dans la limite des contraintes médicales
                - Communication rassurante et continue pendant le travail : expliquer les gestes, donner des repères sur la progression
                - Attention portée à l'état émotionnel : anxiété, peur, fatigue, qui peuvent influencer la perception de la douleur et le déroulement du travail (cercle douleur-anxiété-tension)

                RÔLE CENTRAL DE LA SAGE-FEMME
                La sage-femme adapte les techniques de gestion de la douleur à chaque situation, en combinant souvent plusieurs approches (par exemple : mobilité + respiration + soutien continu, en complément ou en alternative à une analgésie pharmacologique). Une bonne gestion de la douleur contribue à un accouchement plus serein, à une meilleure coopération de la parturiente lors de la phase d'expulsion, et à un vécu positif de la naissance.
                """),
            new LessonSpec("Cas pratique : fiche de consultation prénatale CPN (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 25, """
                Cas clinique : Madame DIALLO, 24 ans, gestité 2 parité 1, se présente pour sa 1ère consultation prénatale à 12 SA. Vous devez saisir ses données dans la fiche CPN et assurer le suivi jusqu'à 36 SA (4 consultations au total selon le calendrier OMS).

                Votre mission :
                1. Remplissez les données d'identification (A4, A5, A6, A7) : âge 24 ans, G2P1, DPA à calculer à partir de la date des dernières règles (DDR), nombre de CPN prévues = 4.
                2. Complétez les données cliniques pour chaque consultation (lignes 11 à 14) :
                   - CPN1 (12 SA) : TA 110/70 → saisir 110 en B11, 70 en C11
                   - CPN2 (20 SA) : TA 115/72 → B12/C12
                   - CPN3 (28 SA) : TA 125/80 → B13/C13
                   - CPN4 (36 SA) : TA 128/82 → B14/C14
                3. Remplissez les poids pour chaque CPN (lignes 17-18) : 55 kg, 57,5 kg, 60 kg, 63 kg.
                4. La cellule B19 calcule automatiquement la prise de poids totale. Comparez à la norme (8-14 kg).
                5. En colonne G, notez pour chaque CPN : normal / surveillance / alerte selon les valeurs de TA (alerte si TA diastolique > 90 mmHg).

                Rappel : en rouge si TA diastolique ≥ 90 mmHg (prééclampsie à redouter).
                """, """
                {"cells":{
                  "A1":{"raw":"FICHE DE CONSULTATION PRÉNATALE (CPN)","bold":true},
                  "A3":{"raw":"IDENTIFICATION","bold":true},
                  "A4":{"raw":"Prénom/Nom de la patiente :"},
                  "A5":{"raw":"Âge :"},
                  "A6":{"raw":"Gestité / Parité :"},
                  "A7":{"raw":"DPA (date présumée d'accouchement) :"},
                  "A8":{"raw":"Nombre de CPN prévues :"},
                  "A10":{"raw":"TENSION ARTÉRIELLE (mmHg)","bold":true},
                  "B10":{"raw":"TA Systolique","bold":true},
                  "C10":{"raw":"TA Diastolique","bold":true},
                  "G10":{"raw":"Observations","bold":true},
                  "A11":{"raw":"CPN 1 (12 SA)"},
                  "A12":{"raw":"CPN 2 (20 SA)"},
                  "A13":{"raw":"CPN 3 (28 SA)"},
                  "A14":{"raw":"CPN 4 (36 SA)"},
                  "A16":{"raw":"POIDS (kg)","bold":true},
                  "B16":{"raw":"CPN 1","bold":true},
                  "C16":{"raw":"CPN 2","bold":true},
                  "D16":{"raw":"CPN 3","bold":true},
                  "E16":{"raw":"CPN 4","bold":true},
                  "A17":{"raw":"Poids"},
                  "A19":{"raw":"Prise de poids totale :","bold":true},
                  "B19":{"raw":"=E17-B17","bold":true,"format":"number"}
                }}
                """),
            new LessonSpec("Quiz : Suivi prénatal et accouchement", Lesson.LessonType.QUIZ, 15, """
                Ce quiz couvre l'ensemble du module "Suivi Prénatal et Accouchement" :

                - Le calendrier des consultations prénatales recommandé par l'OMS (8 contacts) et le contenu type d'une CPN
                - Les signes annonçant le début du travail et les trois stades de l'accouchement (dilatation, expulsion, délivrance)
                - Les principales étapes de l'accouchement eutocique et l'accueil du nouveau-né (score d'Apgar, peau à peau, clampage du cordon)
                - Les méthodes pharmacologiques et non pharmacologiques de gestion de la douleur, et le rôle de l'accompagnement continu de la parturiente

                Avant de répondre, assurez-vous de pouvoir :
                - Différencier les contractions de Braxton-Hicks des contractions du travail
                - Décrire les trois stades de l'accouchement et leurs caractéristiques principales
                - Citer au moins trois signes de danger devant amener une femme enceinte à consulter en urgence
                - Expliquer le principe de la délivrance dirigée et son intérêt dans la prévention de l'hémorragie du post-partum

                Conseil : la leçon "Signes du travail et phases de l'accouchement" est particulièrement importante pour ce quiz, relisez-la attentivement si besoin.

                Bon courage !
                """)
        );
    }

    private List<LessonSpec> sageFemmeCours3Lessons() {
        return List.of(
            new LessonSpec("Examen clinique du nouveau-né à la naissance", Lesson.LessonType.VIDEO, 25, """
                L'examen clinique du nouveau-né à la naissance permet d'évaluer son adaptation à la vie extra-utérine et de détecter précocement toute anomalie nécessitant une prise en charge immédiate.

                LE SCORE D'APGAR
                Évalué à 1 minute et à 5 minutes de vie (et parfois à 10 minutes si besoin), le score d'Apgar attribue une note de 0 à 2 pour cinq critères :
                - Apparence (couleur de la peau) : bleue/pâle (0), corps rose extrémités bleues (1), entièrement rose (2)
                - Pouls (fréquence cardiaque) : absent (0), < 100/min (1), > 100/min (2)
                - Grimace (réactivité aux stimulations) : absente (0), faible (1), vive avec cri/toux (2)
                - Activité (tonus musculaire) : flasque (0), légère flexion (1), mouvements actifs (2)
                - Respiration : absente (0), faible/irrégulière (1), vigoureuse avec cri (2)

                Score total sur 10 :
                - 7 à 10 : bonne adaptation
                - 4 à 6 : adaptation modérément perturbée, nécessitant une surveillance et parfois des gestes de stimulation
                - 0 à 3 : détresse sévère, nécessitant une réanimation néonatale immédiate

                EXAMEN MORPHOLOGIQUE COMPLET
                - Mensurations : poids de naissance (normal entre 2 500 g et 4 000 g), taille, périmètre crânien
                - Examen de la peau : coloration, présence de marques de naissance, signes d'infection
                - Examen de la tête : fontanelles, sutures crâniennes, recherche de bosse séro-sanguine ou de céphalhématome
                - Examen cardio-pulmonaire : auscultation cardiaque (recherche de souffle), fréquence respiratoire, recherche de signes de détresse respiratoire (geignement, tirage, battement des ailes du nez)
                - Examen abdominal : palpation, vérification de l'aspect du cordon ombilical
                - Examen des organes génitaux externes et recherche de malformations
                - Examen des membres : recherche d'une luxation congénitale de la hanche (manœuvres de Barlow et Ortolani), compte des doigts et orteils
                - Examen neurologique : tonus, réflexes archaïques (réflexe de succion, de grasping, de Moro, des points cardinaux)

                SOINS IMMÉDIATS À LA NAISSANCE
                - Séchage immédiat et maintien de la chaleur (prévention de l'hypothermie)
                - Désobstruction des voies aériennes si nécessaire
                - Contact peau à peau précoce avec la mère
                - Soins du cordon ombilical (section, clampage, surveillance de l'absence de saignement)
                - Administration de vitamine K (prévention de la maladie hémorragique du nouveau-né) et de soins oculaires préventifs selon les protocoles
                - Identification du nouveau-né (bracelet d'identification)

                Cet examen initial conditionne la suite de la prise en charge : un nouveau-né en bonne santé pourra être mis au sein précocement (sujet de la leçon suivante), tandis qu'un nouveau-né présentant des signes d'alerte sera orienté vers une surveillance ou une prise en charge spécialisée.
                """),
            new LessonSpec("Allaitement maternel et nutrition du nourrisson", Lesson.LessonType.PDF, 25, """
                L'allaitement maternel est recommandé par l'OMS comme mode d'alimentation exclusif pendant les six premiers mois de vie, en raison de ses bénéfices nutritionnels, immunitaires et affectifs, tant pour le nourrisson que pour la mère.

                COMPOSITION ET BÉNÉFICES DU LAIT MATERNEL
                - Le colostrum (premiers jours) : riche en anticorps (immunoglobulines, notamment IgA sécrétoires), en protéines et en facteurs de croissance, il joue un rôle protecteur essentiel pour le système immunitaire immature du nouveau-né
                - Le lait mature : équilibre adapté en protéines, lipides (essentiels pour le développement cérébral), glucides (lactose) et micronutriments, dont la composition évolue au fil de la tétée et de la croissance de l'enfant
                - Bénéfices pour l'enfant : réduction du risque d'infections digestives et respiratoires, diminution du risque d'allergies, contribution au développement cognitif
                - Bénéfices pour la mère : favorise l'involution utérine (par libération d'ocytocine), réduit le risque d'hémorragie du post-partum, espacement naturel des naissances (sous conditions), réduction à long terme du risque de certains cancers

                MISE EN PLACE DE L'ALLAITEMENT
                - Mise au sein précoce, idéalement dans la première heure de vie ("heure dorée"), favorisant la montée de lait et le lien mère-enfant
                - Allaitement à la demande, sans restriction d'horaires ni de durée
                - Vérification de la bonne position et de la bonne prise du sein (le nourrisson doit prendre une grande partie de l'aréole, pas seulement le mamelon) pour assurer une tétée efficace et prévenir les crevasses
                - Encouragement de la cohabitation mère-enfant (rooming-in) pour favoriser l'allaitement à la demande

                SIGNES D'UN ALLAITEMENT EFFICACE
                - Tétées fréquentes (8 à 12 fois par 24h chez le nouveau-né)
                - Déglutition audible pendant la tétée
                - Selles et urines régulières
                - Prise de poids satisfaisante après la perte de poids physiologique initiale (récupération du poids de naissance vers J10-J14)

                DIFFICULTÉS COURANTES ET CONDUITE À TENIR
                - Engorgement mammaire : vidange régulière du sein, application de chaleur avant et de froid après la tétée
                - Crevasses du mamelon : correction de la position du nourrisson, application de lait maternel sur le mamelon
                - Insuffisance de lactation perçue : stimulation par tétées fréquentes, soutien psychologique de la mère

                RÔLE DE LA SAGE-FEMME
                La sage-femme accompagne et soutient la mère dans la mise en place de l'allaitement, corrige les positions inadéquates, rassure face aux difficultés courantes, et oriente vers une consultation spécialisée (lactation) en cas de difficultés persistantes. Elle informe également sur la diversification alimentaire à partir de 6 mois, en complément de la poursuite de l'allaitement.
                """),
            new LessonSpec("Surveillance du post-partum et complications", Lesson.LessonType.VIDEO, 30, """
                Le post-partum (ou suites de couches) désigne la période s'étendant de l'accouchement jusqu'au retour de couches (réapparition des règles), généralement 6 à 8 semaines. C'est une période de transformations physiologiques majeures mais aussi de risques de complications, justifiant une surveillance rigoureuse.

                LE POST-PARTUM IMMÉDIAT (2 HEURES SUIVANT L'ACCOUCHEMENT)
                Cette période est critique pour le dépistage de l'hémorragie du post-partum (HPP), première cause de mortalité maternelle dans le monde. La surveillance porte sur :
                - Le globe utérin : l'utérus doit être bien contracté, ferme, en dessous de l'ombilic (un utérus mou ou non rétracté = signe d'alerte)
                - Les saignements : quantité et aspect des pertes vaginales
                - Les constantes vitales : pouls, tension artérielle, conscience
                - La vessie : une vessie pleine peut gêner la rétraction utérine

                INVOLUTION UTÉRINE
                Après l'accouchement, l'utérus subit une involution progressive : son poids passe d'environ 1000 g juste après l'accouchement à environ 60-80 g six semaines plus tard. La hauteur utérine diminue d'environ 1 cm par jour. Les lochies (écoulements vaginaux post-partum) évoluent en couleur (rouges puis brunâtres puis blanchâtres) et en quantité au fil des jours.

                COMPLICATIONS À SURVEILLER

                1. Hémorragie du post-partum (HPP) : pertes sanguines supérieures à 500 ml après un accouchement par voie basse. Les principales causes sont l'atonie utérine (cause la plus fréquente), les déchirures génitales, la rétention placentaire et les troubles de la coagulation. La prise en charge repose sur le massage utérin, l'administration d'utérotoniques, et la recherche de la cause.

                2. Infections puerpérales : fièvre survenant dans les jours suivant l'accouchement, pouvant être due à une endométrite, une infection de plaie périnéale ou de cicatrice de césarienne, une infection urinaire ou une mastite. Une fièvre persistante doit être explorée et traitée rapidement.

                3. Thrombose veineuse profonde et embolie pulmonaire : la grossesse et le post-partum sont des périodes à risque thromboembolique accru ; surveillance des signes (douleur, œdème, rougeur d'un membre inférieur).

                4. Troubles psychiques du post-partum : le "baby blues" (passager, dans les premiers jours) doit être différencié de la dépression du post-partum (plus durable, nécessitant une prise en charge) et de la psychose puerpérale (urgence psychiatrique).

                SURVEILLANCE GLOBALE DE LA MÈRE
                - Constantes vitales régulières
                - Surveillance du périnée (cicatrisation d'une épisiotomie ou déchirure)
                - Surveillance des seins (engorgement, signes de mastite)
                - Surveillance du transit et de la diurèse
                - Soutien à l'allaitement et au lien mère-enfant
                - Dépistage précoce des signes de détresse psychologique

                Une surveillance attentive et systématique du post-partum permet de prévenir, détecter et traiter précocement les complications, contribuant directement à la réduction de la mortalité maternelle.
                """),
            new LessonSpec("Vaccination et suivi pédiatrique précoce", Lesson.LessonType.PDF, 20, """
                Le suivi pédiatrique précoce vise à accompagner la croissance et le développement du nourrisson, à dépister précocement les anomalies, et à assurer une protection vaccinale conforme au calendrier national.

                LE PROGRAMME ÉLARGI DE VACCINATION (PEV)
                Le calendrier vaccinal type recommandé pour les nourrissons (calendrier représentatif de nombreux programmes nationaux en Afrique de l'Ouest) comprend notamment :

                À LA NAISSANCE :
                - BCG (tuberculose) - dose unique
                - Polio oral (VPO) - dose 0
                - Hépatite B - 1ère dose (selon les schémas, parfois intégrée au pentavalent)

                À 6 SEMAINES :
                - Pentavalent (DTC-HepB-Hib) - 1ère dose
                - Polio oral - dose 1
                - Pneumocoque (PCV) - 1ère dose
                - Rotavirus - 1ère dose

                À 10 SEMAINES :
                - Pentavalent - 2e dose
                - Polio oral - dose 2
                - Pneumocoque - 2e dose
                - Rotavirus - 2e dose

                À 14 SEMAINES :
                - Pentavalent - 3e dose
                - Polio oral - dose 3 et VPI (polio injectable)
                - Pneumocoque - 3e dose

                À 9 MOIS :
                - Rougeole-Rubéole (RR) - 1ère dose
                - Fièvre jaune
                - Méningite A (selon contexte)

                CONSULTATIONS DE SUIVI DU NOURRISSON
                À chaque consultation de suivi (généralement aux mêmes échéances que les vaccinations, puis régulièrement jusqu'à 5 ans) :
                - Mesures anthropométriques : poids, taille, périmètre crânien, report sur les courbes de croissance (carnet de santé)
                - Évaluation du développement psychomoteur selon l'âge : tenue de tête, sourire-réponse, tenue assise, marche, premiers mots...
                - Examen clinique général : recherche de signes de malnutrition, d'anémie, d'infections
                - Vérification du statut vaccinal et mise à jour si nécessaire
                - Conseils nutritionnels : poursuite de l'allaitement, introduction de la diversification alimentaire à partir de 6 mois
                - Dépistage de signes d'alerte : retard de croissance, retard de développement, signes de maladie

                ÉDUCATION DES PARENTS
                - Signes devant amener à consulter en urgence : fièvre élevée, refus de téter, convulsions, difficultés respiratoires, diarrhée avec déshydratation
                - Importance de l'hygiène (lavage des mains, hygiène du cordon ombilical jusqu'à sa chute)
                - Prévention du paludisme chez le nourrisson (moustiquaire imprégnée)
                - Importance du respect du calendrier vaccinal pour la protection individuelle et collective (immunité de groupe)

                RÔLE DE LA SAGE-FEMME
                La sage-femme joue un rôle clé dans l'éducation des parents, la promotion de la vaccination, le dépistage précoce des troubles de croissance ou de développement, et l'orientation vers les services pédiatriques en cas d'anomalie. Ce suivi précoce pose les bases d'une croissance et d'un développement harmonieux de l'enfant.
                """),
            new LessonSpec("Cas pratique : score APGAR et surveillance néonatale (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 20, """
                Cas clinique : Nouveau-né de Mme DIALLO, né par accouchement eutocique à 38 SA + 5 jours. Poids de naissance : 3 100 g. Vous devez évaluer le score APGAR et assurer la surveillance pondérale des premiers jours.

                Votre mission — Partie 1 : Score APGAR
                1. Cotez les 5 critères APGAR à 1 minute en saisissant 0, 1 ou 2 dans les cellules B4 à B8, selon les observations suivantes :
                   - FC : 110 bpm (score 2) | Respiration : cri vigoureux (2) | Tonus : actif (2) | Réactivité : éternuement (2) | Coloration : extrémités légèrement bleues (1)
                2. À 5 minutes (C4:C8) : FC 130 (2), Respiration vigoureux (2), Tonus actif (2), Réactivité (2), Coloration rose (2).
                3. À 10 minutes (D4:D8) : tous à 2.
                4. Les cellules B9, C9, D9 totalisent automatiquement chaque score via SOMME. Interprétez : ≥ 7 = normal ; 4-6 = réanimation légère ; < 4 = réanimation urgente.

                Votre mission — Partie 2 : Suivi pondéral
                5. Saisissez les poids journaliers (en grammes) dans les cellules B13 à F13 :
                   J1 : 3 050 g | J2 : 2 990 g | J3 : 2 980 g | J4 : 3 020 g | J5 : 3 080 g
                6. La ligne 14 calcule automatiquement la variation par rapport au poids de naissance (B13 - 3100).
                   Une perte de poids de plus de 10 % du poids de naissance (< 2 790 g) est une alerte. Y a-t-il une alerte dans ce cas ?

                Résultats attendus : APGAR 1 min = 9, 5 min = 10, 10 min = 10. Perte maximale = 120 g (3,9 %) — pas d'alerte.
                """, """
                {"cells":{
                  "A1":{"raw":"SCORE APGAR ET SURVEILLANCE NÉONATALE","bold":true},
                  "A2":{"raw":"Nouveau-né de Mme DIALLO — Naissance à 38 SA+5 — Poids : 3 100 g"},
                  "A3":{"raw":"CRITÈRE APGAR","bold":true},
                  "B3":{"raw":"1 min","bold":true},
                  "C3":{"raw":"5 min","bold":true},
                  "D3":{"raw":"10 min","bold":true},
                  "E3":{"raw":"Barème","bold":true},
                  "A4":{"raw":"Fréquence cardiaque"},
                  "E4":{"raw":"0 : absent | 1 : < 100 | 2 : ≥ 100"},
                  "A5":{"raw":"Respiration"},
                  "E5":{"raw":"0 : absent | 1 : irrégulière | 2 : cri vigoureux"},
                  "A6":{"raw":"Tonus musculaire"},
                  "E6":{"raw":"0 : flasque | 1 : flexion légère | 2 : mouvements actifs"},
                  "A7":{"raw":"Réactivité (stimuli)"},
                  "E7":{"raw":"0 : nulle | 1 : grimace | 2 : toux / éternuement"},
                  "A8":{"raw":"Coloration"},
                  "E8":{"raw":"0 : bleu/blanc | 1 : extrémités bleues | 2 : entièrement rose"},
                  "A9":{"raw":"SCORE TOTAL","bold":true},
                  "B9":{"raw":"=SOMME(B4:B8)","bold":true,"format":"number"},
                  "C9":{"raw":"=SOMME(C4:C8)","bold":true,"format":"number"},
                  "D9":{"raw":"=SOMME(D4:D8)","bold":true,"format":"number"},
                  "A11":{"raw":"SUIVI PONDÉRAL (grammes)","bold":true},
                  "A12":{"raw":"Jour","bold":true},
                  "B12":{"raw":"J1","bold":true},
                  "C12":{"raw":"J2","bold":true},
                  "D12":{"raw":"J3","bold":true},
                  "E12":{"raw":"J4","bold":true},
                  "F12":{"raw":"J5","bold":true},
                  "A13":{"raw":"Poids (g)"},
                  "A14":{"raw":"Variation / naissance","bold":true},
                  "B14":{"raw":"=B13-3100","format":"number"},
                  "C14":{"raw":"=C13-3100","format":"number"},
                  "D14":{"raw":"=D13-3100","format":"number"},
                  "E14":{"raw":"=E13-3100","format":"number"},
                  "F14":{"raw":"=F13-3100","format":"number"}
                }}
                """),
            new LessonSpec("Quiz : Nouveau-né et post-partum", Lesson.LessonType.QUIZ, 15, """
                Ce quiz porte sur l'ensemble du module "Soins du Nouveau-né et Post-partum" :

                - Le score d'Apgar et son interprétation, ainsi que les principaux éléments de l'examen clinique du nouveau-né
                - Les bénéfices de l'allaitement maternel, la composition du lait maternel (colostrum vs lait mature) et les signes d'un allaitement efficace
                - La surveillance du post-partum immédiat (globe utérin, saignements) et les principales complications : hémorragie du post-partum, infections puerpérales, troubles thromboemboliques et psychiques
                - Le calendrier vaccinal du nourrisson (PEV) et le contenu des consultations de suivi pédiatrique précoce

                Avant de répondre, assurez-vous de pouvoir :
                - Calculer et interpréter un score d'Apgar à partir d'une description clinique
                - Citer au moins trois bénéfices de l'allaitement maternel pour la mère et pour l'enfant
                - Expliquer pourquoi la surveillance du globe utérin est essentielle dans les heures suivant l'accouchement
                - Citer les principales vaccinations prévues à la naissance et lors des premières consultations

                Conseil : la leçon "Surveillance du post-partum et complications" aborde l'hémorragie du post-partum, une urgence obstétricale majeure à bien maîtriser.

                Bon courage !
                """)
        );
    }

    // ===================== Marketing Digital =====================

    private List<LessonSpec> marketingCours1Lessons() {
        return List.of(
            new LessonSpec("Le marketing digital : définitions, enjeux et écosystème", Lesson.LessonType.VIDEO, 25, """
                Le marketing digital regroupe l'ensemble des techniques marketing mises en œuvre sur les canaux numériques (site web, moteurs de recherche, réseaux sociaux, email, applications mobiles) pour attirer, convertir et fidéliser des clients. Contrairement au marketing traditionnel (affichage, presse, radio), il présente trois avantages majeurs : la mesurabilité (chaque action peut être suivie précisément), le ciblage (on touche une audience précise selon l'âge, la localisation, les centres d'intérêt) et l'accessibilité budgétaire (on peut démarrer une campagne avec un budget modeste et l'ajuster en temps réel).

                Pourquoi le marketing digital est incontournable aujourd'hui :
                - La forte croissance du taux de pénétration mobile et internet en Afrique de l'Ouest
                - L'essor du commerce en ligne et du paiement mobile (Orange Money, Wave, Mobile Money) qui facilite l'achat en ligne
                - Le temps croissant passé par les consommateurs sur les réseaux sociaux (Facebook, WhatsApp, Instagram, TikTok)
                - La possibilité pour une petite entreprise de toucher une audience nationale, voire internationale, sans les coûts d'une campagne média classique

                L'écosystème du marketing digital repose sur plusieurs briques complémentaires :
                - Le site web ou la boutique en ligne : la vitrine centrale de l'entreprise
                - Le référencement naturel (SEO) : être visible gratuitement dans les résultats de recherche
                - La publicité en ligne (SEA, Social Ads) : acheter de la visibilité immédiate
                - Les réseaux sociaux : construire une communauté et une relation de proximité avec les clients
                - L'emailing et les messageries (WhatsApp Business) : fidéliser et relancer les clients
                - L'analyse de données (web analytics) : mesurer et améliorer en continu les performances

                Dans ce premier cours, nous allons poser les bases théoriques (tunnel de conversion, persona, stratégie de contenu) avant d'aborder, dans les cours suivants, le SEO, le growth marketing et la publicité sur les réseaux sociaux.
                """),
            new LessonSpec("Le tunnel de conversion et les canaux d'acquisition digitale", Lesson.LessonType.PDF, 28, """
                Pour construire une stratégie marketing efficace, il faut comprendre le parcours que suit un client, de la découverte de la marque jusqu'à l'achat, puis la fidélisation. Ce parcours est modélisé par le tunnel de conversion (ou entonnoir marketing).

                LES ÉTAPES DU TUNNEL DE CONVERSION
                - Notoriété (Awareness) : le prospect découvre l'existence de la marque ou du produit
                - Considération (Consideration) : il compare les offres, recherche des avis, s'informe
                - Conversion (Conversion) : il passe à l'achat ou réalise l'action souhaitée (inscription, demande de devis)
                - Fidélisation (Retention) : il devient client récurrent
                - Recommandation (Referral) : il recommande la marque à son entourage

                Ce modèle est aussi résumé par le cadre AARRR (utilisé en growth marketing) : Acquisition, Activation, Rétention, Recommandation (Referral), Revenu.

                LES CANAUX D'ACQUISITION DIGITALE
                On classe généralement les canaux en trois catégories :
                1. Owned media (médias possédés) : le site web, le blog, la page Facebook, la liste d'emails - des canaux que l'entreprise contrôle entièrement
                2. Earned media (médias gagnés) : le bouche-à-oreille digital, les partages, les avis clients, les mentions presse - une visibilité obtenue sans paiement direct
                3. Paid media (médias payants) : la publicité sur Google (SEA), sur les réseaux sociaux (Social Ads), le display, les partenariats sponsorisés

                PRINCIPAUX CANAUX ET LEUR RÔLE DANS LE TUNNEL
                - SEO (référencement naturel) : excellent pour la notoriété et la considération, sur le long terme
                - SEA (Google Ads) : efficace pour capter une demande déjà existante (recherche active)
                - Réseaux sociaux organiques : notoriété et fidélisation, construction de communauté
                - Publicité sociale (Facebook/Instagram Ads) : notoriété ciblée et conversion
                - Email marketing : fidélisation et relance, coût très faible par contact
                - Affiliation et partenariats : acquisition à la performance

                Une stratégie digitale performante ne repose jamais sur un seul canal : elle combine plusieurs leviers adaptés à chaque étape du tunnel, avec des objectifs et des indicateurs de succès (KPI) spécifiques à chaque étape.
                """),
            new LessonSpec("Construire un persona d'acheteur et une stratégie de contenu", Lesson.LessonType.VIDEO, 30, """
                Avant de produire le moindre contenu ou de lancer la moindre campagne, il est indispensable de savoir précisément à qui l'on s'adresse. C'est le rôle du persona d'acheteur (buyer persona) : un profil semi-fictif représentant le client idéal, construit à partir de données réelles et d'hypothèses réalistes.

                LES ÉLÉMENTS D'UN PERSONA COMPLET
                - Informations démographiques : âge, sexe, situation familiale, localisation, niveau de revenu
                - Contexte professionnel : métier, secteur d'activité, niveau de responsabilité
                - Objectifs et motivations : que cherche-t-il à accomplir ?
                - Freins et objections : qu'est-ce qui l'empêche d'acheter ou de s'engager ?
                - Comportements digitaux : quels réseaux sociaux utilise-t-il ? À quel moment de la journée est-il actif en ligne ? Quels types de contenus consulte-t-il ?

                Exemple de persona : "Fatou, 32 ans, gère une boutique de vêtements dans le marché Sandaga à Dakar. Elle possède un smartphone Android, utilise WhatsApp et Facebook plusieurs fois par jour, cherche à développer sa clientèle en ligne mais manque de temps et de compétences techniques. Elle est sensible aux témoignages d'autres commerçantes ayant réussi leur transition digitale."

                DE LA CONNAISSANCE DU PERSONA À LA STRATÉGIE DE CONTENU
                Une fois le ou les personas définis, on peut construire une stratégie de contenu alignée sur chaque étape du tunnel de conversion :
                - Étape notoriété : articles de blog, vidéos courtes, publications éducatives répondant à une question fréquente du persona
                - Étape considération : études de cas, comparatifs, témoignages clients, webinaires
                - Étape conversion : pages de vente, démonstrations produit, offres limitées dans le temps
                - Étape fidélisation : newsletters, contenus exclusifs, programme de fidélité

                MÉTHODE EN 4 ÉTAPES POUR BÂTIR UNE STRATÉGIE DE CONTENU
                1. Définir les objectifs (notoriété, génération de leads, ventes) et les KPI associés
                2. Identifier les sujets et formats pertinents pour chaque persona et chaque étape du tunnel
                3. Établir un calendrier de publication réaliste et régulier
                4. Mesurer les performances (vues, engagement, conversions) et ajuster le contenu en continu

                Un contenu pertinent, publié régulièrement et adapté au bon persona, est le socle de toute stratégie digitale durable.
                """),
            new LessonSpec("Étude de cas : la transformation digitale d'une PME agroalimentaire dakaroise", Lesson.LessonType.PDF, 30, """
                ÉTUDE DE CAS : SAVEURS DU TERROIR, PME AGROALIMENTAIRE À DAKAR

                Saveurs du Terroir est une PME sénégalaise qui transforme et commercialise des produits locaux (jus de bissap, confitures de mangue, céréales locales). Jusqu'en 2023, l'entreprise vendait exclusivement via des points de vente physiques et le bouche-à-oreille. Confrontée à une concurrence croissante, elle décide d'engager sa transformation digitale.

                SITUATION DE DÉPART (AVANT)
                - Aucun site web, présence Facebook peu active (200 abonnés, 1 publication par mois)
                - Notoriété limitée à Dakar et sa banlieue
                - Chiffre d'affaires mensuel moyen : 3 500 000 FCFA
                - Aucune donnée client centralisée

                OBJECTIFS FIXÉS (méthode SMART)
                - Spécifique : augmenter les ventes en ligne
                - Mesurable : +30 % de chiffre d'affaires en 6 mois
                - Atteignable : avec un budget marketing de 400 000 FCFA/mois
                - Réaliste : en s'appuyant sur les canaux déjà utilisés par la clientèle cible
                - Temporellement défini : sur un semestre, avec bilan trimestriel

                ACTIONS MISES EN PLACE
                1. Création d'un site vitrine avec catalogue de produits et bouton de commande via WhatsApp
                2. Refonte de la page Facebook : publication de recettes, coulisses de production, témoignages clients (3 publications/semaine)
                3. Lancement de campagnes Facebook/Instagram Ads ciblant les femmes de 25-45 ans à Dakar et Thiès, intéressées par l'alimentation locale et bio
                4. Mise en place d'une liste WhatsApp Business pour les clients fidèles, avec offres exclusives mensuelles

                RÉSULTATS APRÈS 6 MOIS
                - Abonnés Facebook : de 200 à 4 800
                - Trafic mensuel sur le site : 6 200 visites
                - Chiffre d'affaires mensuel : 4 900 000 FCFA (+40 %)
                - Coût d'acquisition moyen par nouveau client : 2 100 FCFA

                LEÇONS À RETENIR
                - La régularité des publications est plus déterminante que leur quantité isolée
                - Le ciblage précis (âge, localisation, centres d'intérêt) réduit fortement le gaspillage publicitaire
                - L'intégration d'un canal de commande simple (WhatsApp) lève une barrière importante à l'achat en ligne
                - Mesurer et ajuster chaque mois permet de réallouer le budget vers les canaux les plus performants

                Ce cas illustre comment une stratégie digitale progressive, avec des objectifs clairs et un budget modeste mais bien ciblé, peut transformer durablement les ventes d'une PME.
                """),
            new LessonSpec("Pratique : calculer le CPC, le CPM et le ROI d'une campagne publicitaire (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 22, """
                Mettez en pratique le calcul des indicateurs clés d'une campagne publicitaire digitale : le CPC (coût par clic), le CPM (coût pour mille impressions) et le ROI (retour sur investissement).

                Un tableau "Performance des campagnes publicitaires" est déjà prêt avec les colonnes Canal, Impressions, Clics, Coût (FCFA), CPC, CPM, Conversions, Revenu (FCFA) et ROI, ainsi qu'une ligne TOTAUX (ligne 5) qui additionne automatiquement les colonnes Impressions, Clics, Coût, Conversions et Revenu grâce à la formule =SOMME(...).

                Les données brutes de 3 campagnes (Google Ads Search, Facebook Ads, Instagram Ads) sont déjà saisies dans les colonnes Impressions, Clics, Coût, Conversions et Revenu (lignes 2 à 4).

                Votre mission :
                1. Dans la colonne CPC (colonne E), calculez le coût par clic de chaque campagne avec la formule : Coût / Clics
                2. Dans la colonne CPM (colonne F), calculez le coût pour mille impressions avec la formule : (Coût / Impressions) × 1000
                3. Dans la colonne ROI (colonne I), calculez le retour sur investissement avec la formule : (Revenu - Coût) / Coût
                4. Complétez la ligne TOTAUX pour le CPC, le CPM et le ROI global de l'ensemble des campagnes (mêmes formules appliquées aux totaux)
                5. Identifiez, dans une note personnelle, quelle campagne a le meilleur ROI et laquelle mérite d'être réduite ou arrêtée

                Rappel des formules :
                - CPC = Coût total / Nombre de clics
                - CPM = (Coût total / Nombre d'impressions) × 1000
                - ROI = (Revenu généré - Coût investi) / Coût investi

                Une fois toutes les formules saisies, vérifiez que la ligne TOTAUX affiche des valeurs cohérentes avec la moyenne pondérée des 3 campagnes. Cliquez ensuite sur "Marquer comme terminé".
                """, """
                {"cells":{
                  "A1":{"raw":"Canal","bold":true},
                  "B1":{"raw":"Impressions","bold":true},
                  "C1":{"raw":"Clics","bold":true},
                  "D1":{"raw":"Coût (FCFA)","bold":true},
                  "E1":{"raw":"CPC","bold":true},
                  "F1":{"raw":"CPM","bold":true},
                  "G1":{"raw":"Conversions","bold":true},
                  "H1":{"raw":"Revenu (FCFA)","bold":true},
                  "I1":{"raw":"ROI","bold":true},
                  "A2":{"raw":"Google Ads Search"},
                  "B2":{"raw":"150000"},
                  "C2":{"raw":"4500"},
                  "D2":{"raw":"675000","format":"currency"},
                  "G2":{"raw":"90"},
                  "H2":{"raw":"2250000","format":"currency"},
                  "A3":{"raw":"Facebook Ads"},
                  "B3":{"raw":"300000"},
                  "C3":{"raw":"6000"},
                  "D3":{"raw":"480000","format":"currency"},
                  "G3":{"raw":"60"},
                  "H3":{"raw":"1200000","format":"currency"},
                  "A4":{"raw":"Instagram Ads"},
                  "B4":{"raw":"250000"},
                  "C4":{"raw":"3750"},
                  "D4":{"raw":"337500","format":"currency"},
                  "G4":{"raw":"45"},
                  "H4":{"raw":"900000","format":"currency"},
                  "A5":{"raw":"TOTAUX","bold":true},
                  "B5":{"raw":"=SOMME(B2:B4)","bold":true},
                  "C5":{"raw":"=SOMME(C2:C4)","bold":true},
                  "D5":{"raw":"=SOMME(D2:D4)","bold":true,"format":"currency"},
                  "G5":{"raw":"=SOMME(G2:G4)","bold":true},
                  "H5":{"raw":"=SOMME(H2:H4)","bold":true,"format":"currency"}
                }}
                """),
            new LessonSpec("Quiz : Les fondamentaux du marketing digital", Lesson.LessonType.QUIZ, 15, """
                Ce quiz vous permet de vérifier votre compréhension des notions essentielles abordées dans ce module :

                - La définition du marketing digital et les avantages qu'il offre par rapport au marketing traditionnel (mesurabilité, ciblage, budget flexible)
                - Les composantes de l'écosystème digital : site web, SEO, SEA, réseaux sociaux, email marketing
                - Les étapes du tunnel de conversion (notoriété, considération, conversion, fidélisation, recommandation) et le cadre AARRR
                - La distinction entre owned media, earned media et paid media
                - La construction d'un persona d'acheteur et son utilisation pour bâtir une stratégie de contenu adaptée à chaque étape du tunnel
                - Les facteurs clés de succès d'une transformation digitale de PME (objectifs SMART, ciblage précis, régularité, mesure des résultats)

                Avant de répondre, relisez si besoin les leçons précédentes : "Le marketing digital : définitions, enjeux et écosystème", "Le tunnel de conversion et les canaux d'acquisition digitale", "Construire un persona d'acheteur et une stratégie de contenu" et "Étude de cas : la transformation digitale d'une PME agroalimentaire dakaroise".

                Conseil : pour chaque question, demandez-vous d'abord à quelle étape du tunnel de conversion se rapporte la situation décrite, puis identifiez le canal ou l'indicateur le plus adapté.

                Bonne chance !
                """)
        );
    }

    private List<LessonSpec> marketingCours2Lessons() {
        return List.of(
            new LessonSpec("Comment fonctionnent les moteurs de recherche : crawl, index, classement", Lesson.LessonType.VIDEO, 25, """
                Le référencement naturel (SEO - Search Engine Optimization) consiste à améliorer la visibilité d'un site web dans les résultats gratuits (organiques) des moteurs de recherche comme Google. Pour bien optimiser un site, il faut d'abord comprendre comment un moteur de recherche fonctionne.

                LES 3 GRANDES ÉTAPES DU FONCTIONNEMENT D'UN MOTEUR DE RECHERCHE

                1. LE CRAWL (exploration)
                Google utilise des robots (appelés « crawlers » ou « Googlebot ») qui parcourent en permanence le web en suivant les liens d'une page à l'autre, afin de découvrir de nouvelles pages et de détecter les mises à jour de contenu.

                2. L'INDEXATION
                Une fois une page explorée, Google analyse son contenu (texte, images, structure) et l'ajoute à son index - une immense base de données regroupant des milliards de pages web. Une page qui n'est pas indexée ne peut jamais apparaître dans les résultats de recherche.

                3. LE CLASSEMENT (ranking)
                Lorsqu'un internaute effectue une recherche, l'algorithme de Google sélectionne, parmi les pages indexées, celles qui répondent le mieux à la requête, puis les classe selon plus de 200 critères (facteurs de classement).

                LES GRANDES FAMILLES DE FACTEURS DE CLASSEMENT
                - La pertinence du contenu par rapport à la requête de l'internaute (mots-clés, intention de recherche)
                - La qualité technique du site (vitesse de chargement, compatibilité mobile, sécurité HTTPS)
                - L'autorité du site (nombre et qualité des liens externes pointant vers le site - les « backlinks »)
                - L'expérience utilisateur (taux de rebond, temps passé sur la page, facilité de navigation)

                POURQUOI LE SEO EST UN INVESTISSEMENT DE LONG TERME
                Contrairement à la publicité payante (SEA), dont l'effet s'arrête dès que le budget s'épuise, le SEO construit une visibilité durable : une page bien positionnée continue de générer du trafic gratuit pendant des mois, voire des années, sans coût additionnel par clic. C'est pourquoi le SEO est considéré comme un pilier essentiel du growth marketing, cette approche qui vise une croissance rapide et durable en combinant acquisition, activation et rétention.

                Dans les prochaines leçons, nous verrons en détail les trois piliers techniques du SEO, puis la méthode de recherche de mots-clés et d'optimisation on-page.
                """),
            new LessonSpec("Les 3 piliers du SEO : technique, contenu et netlinking", Lesson.LessonType.PDF, 30, """
                Une stratégie SEO complète repose sur trois piliers indissociables. Négliger l'un d'eux limite fortement les résultats, même si les deux autres sont bien maîtrisés.

                PILIER 1 : LE SEO TECHNIQUE
                Il s'agit de s'assurer que le site est facilement explorable et compréhensible par les moteurs de recherche, et agréable à utiliser pour les visiteurs.
                - Vitesse de chargement des pages (idéalement moins de 3 secondes)
                - Compatibilité mobile (« mobile-first » : Google indexe en priorité la version mobile du site)
                - Certificat de sécurité HTTPS
                - Structure claire des URL et du maillage interne (liens entre les pages du site)
                - Fichier sitemap.xml et fichier robots.txt correctement configurés
                - Absence d'erreurs 404 et de liens cassés

                PILIER 2 : LE CONTENU
                Le contenu est ce qui répond réellement au besoin de l'internaute. Un contenu de qualité doit :
                - Répondre précisément à l'intention de recherche (informationnelle, transactionnelle, navigationnelle)
                - Être structuré avec des titres hiérarchisés (balises H1, H2, H3)
                - Contenir les mots-clés pertinents de façon naturelle, sans « bourrage de mots-clés »
                - Être régulièrement mis à jour et enrichi
                - Apporter une valeur ajoutée réelle (exemples concrets, données chiffrées, réponses à des questions fréquentes)

                PILIER 3 : LE NETLINKING (autorité)
                Le netlinking consiste à obtenir des liens depuis d'autres sites de qualité pointant vers le vôtre. Google interprète chaque lien entrant comme un « vote de confiance ».
                - Privilégier la qualité à la quantité : un lien depuis un site reconnu du secteur vaut plus que dix liens depuis des sites peu fiables
                - Techniques courantes : partenariats avec des blogs spécialisés, relations presse digitales, annuaires professionnels sérieux, contenu invité (guest blogging)
                - Éviter absolument l'achat massif de liens de mauvaise qualité, sanctionné par Google

                TABLEAU RÉCAPITULATIF DES PRIORITÉS SELON LE STADE DU SITE
                - Site récent : priorité au SEO technique et à la production de contenu de base
                - Site en croissance : priorité au contenu approfondi et au maillage interne
                - Site établi : priorité au netlinking et à l'amélioration continue du contenu existant

                Un audit SEO régulier (au moins tous les 6 mois) permet de vérifier l'état de ces trois piliers et d'identifier les priorités d'action.
                """),
            new LessonSpec("Recherche de mots-clés et optimisation on-page", Lesson.LessonType.VIDEO, 32, """
                La recherche de mots-clés est l'étape fondatrice de toute stratégie SEO : elle permet d'identifier les termes exacts que tapent les internautes lorsqu'ils recherchent un produit, un service ou une information liée à votre activité.

                LES TYPES D'INTENTION DE RECHERCHE
                - Intention informationnelle : l'internaute cherche à apprendre ("comment choisir un bon smartphone")
                - Intention navigationnelle : il cherche un site précis ("page Facebook Orange Sénégal")
                - Intention transactionnelle : il est prêt à acheter ("acheter smartphone Samsung Dakar livraison")
                - Intention commerciale : il compare avant d'acheter ("meilleur smartphone moins de 100000 FCFA")

                MÉTHODE DE RECHERCHE DE MOTS-CLÉS EN 4 ÉTAPES
                1. Lister les sujets principaux liés à votre activité (« brainstorming » de thématiques)
                2. Utiliser des outils de suggestion (Google Suggest, Google Trends, Ubersuggest, la section « Autres questions posées » de Google) pour trouver les variations et questions réelles des internautes
                3. Analyser le volume de recherche mensuel et la difficulté de positionnement de chaque mot-clé
                4. Prioriser les mots-clés de longue traîne (expressions précises de 3 mots ou plus, moins concurrentielles mais avec une intention plus claire), en complément des mots-clés génériques

                Exemple : plutôt que de viser uniquement le mot-clé très concurrentiel « chaussures », une boutique en ligne ciblera aussi « chaussures de sport femme pointure 38 Dakar livraison rapide », un mot-clé de longue traîne avec moins de concurrence et un internaute proche de l'achat.

                L'OPTIMISATION ON-PAGE
                Une fois les mots-clés choisis, il faut les intégrer intelligemment dans chaque page :
                - La balise Title (titre affiché dans les résultats de recherche) : inclure le mot-clé principal, idéalement en début de titre, en moins de 60 caractères
                - La méta-description (résumé affiché sous le titre) : rédiger un texte incitatif de 150-160 caractères incluant le mot-clé
                - La balise H1 : un seul H1 par page, reprenant le mot-clé principal
                - Les balises H2/H3 : structurer le contenu en sous-thèmes intégrant des mots-clés secondaires
                - Les URL : courtes, lisibles, contenant le mot-clé (exemple : /chaussures-sport-femme plutôt que /page.php?id=452)
                - Les images : nommer les fichiers et remplir l'attribut « alt » avec des descriptions incluant les mots-clés pertinents

                Une optimisation on-page rigoureuse, combinée à une recherche de mots-clés pertinente, constitue la base de tout bon positionnement dans les résultats de recherche.
                """),
            new LessonSpec("Étude de cas : la refonte SEO d'une boutique en ligne de mode", Lesson.LessonType.PDF, 32, """
                ÉTUDE DE CAS : KOLOR MODE, BOUTIQUE DE MODE EN LIGNE

                Kolor Mode est une boutique en ligne ivoirienne spécialisée dans la mode africaine contemporaine. Lancée en 2022, elle générait un trafic organique quasi inexistant (moins de 150 visites/mois via Google) malgré un catalogue de plus de 300 produits.

                DIAGNOSTIC INITIAL (AUDIT SEO)
                - Temps de chargement moyen des pages : 6,8 secondes (largement au-dessus du seuil recommandé de 3 secondes)
                - Aucune balise Title ni méta-description personnalisée (toutes les pages produits affichaient le même titre générique)
                - Fiches produits très courtes (30-40 mots), sans contenu éditorial
                - Aucun lien externe pointant vers le site (autorité de domaine quasi nulle)
                - Site non optimisé pour mobile alors que 85 % du trafic potentiel provenait de smartphones

                PLAN D'ACTION SUR 4 MOIS

                Mois 1 - SEO technique :
                - Compression des images et mise en place d'un système de cache, réduisant le temps de chargement à 2,4 secondes
                - Correction de l'affichage mobile (design responsive)

                Mois 2 - Recherche de mots-clés et contenu :
                - Identification de 45 mots-clés de longue traîne pertinents (exemple : « robe wax femme taille 42 livraison Abidjan »)
                - Réécriture des 300 fiches produits avec des descriptions de 150 à 200 mots, incluant les mots-clés ciblés
                - Création d'un blog avec des articles de conseils mode (« comment associer un boubou moderne »)

                Mois 3 - Optimisation on-page :
                - Réécriture de toutes les balises Title et méta-descriptions
                - Restructuration des catégories avec un maillage interne cohérent

                Mois 4 - Netlinking :
                - Partenariats avec 5 blogs mode et lifestyle ivoiriens pour des articles invités avec liens vers le site
                - Inscription dans 3 annuaires professionnels sérieux du secteur e-commerce

                RÉSULTATS APRÈS 4 MOIS
                - Trafic organique mensuel : de 150 à 2 900 visites (+1833 %)
                - Nombre de mots-clés positionnés en première page Google : de 2 à 68
                - Taux de conversion du trafic organique : 3,2 %
                - Chiffre d'affaires généré par le SEO : environ 1 800 000 FCFA sur le 4e mois, contre quasiment 0 au départ

                LEÇONS À RETENIR
                - Le SEO technique est un prérequis : aucun contenu, même excellent, ne compense un site trop lent ou non adapté au mobile
                - Le contenu de qualité sur chaque fiche produit a un impact direct sur le positionnement et la conversion
                - Les résultats SEO ne sont jamais immédiats : ils se construisent sur plusieurs mois, avec des effets qui s'accumulent
                - Le netlinking de qualité, même limité en volume, peut significativement renforcer l'autorité d'un jeune site
                """),
            new LessonSpec("Pratique : suivre les KPI SEO et calculer le taux de conversion organique (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 22, """
                Mettez en pratique le suivi des indicateurs de performance SEO (KPI) d'un site e-commerce sur 3 mois.

                Un tableau "Suivi mensuel du trafic organique" est déjà prêt avec les colonnes Mois, Trafic organique (visites), Mots-clés en 1ère page, Conversions, Taux de conversion et Revenu généré (FCFA), ainsi qu'une ligne TOTAUX (ligne 5) qui additionne automatiquement le Trafic organique, les Conversions et le Revenu grâce à la formule =SOMME(...).

                Les données brutes de 3 mois (Mois 1, Mois 2, Mois 3) sont déjà saisies dans les colonnes Trafic organique, Mots-clés en 1ère page, Conversions et Revenu généré (lignes 2 à 4).

                Votre mission :
                1. Dans la colonne Taux de conversion (colonne E), calculez pour chaque mois le taux de conversion avec la formule : Conversions / Trafic organique
                2. Dans la cellule E5 (ligne TOTAUX), calculez le taux de conversion moyen global avec la formule : Total des conversions / Total du trafic
                3. Dans la colonne G (Revenu moyen par visite), calculez pour chaque mois : Revenu généré / Trafic organique
                4. Observez l'évolution du nombre de mots-clés en première page d'un mois à l'autre et mettez-la en relation avec l'évolution du trafic et du revenu

                Rappel des formules :
                - Taux de conversion = Nombre de conversions / Nombre de visites
                - Revenu moyen par visite = Revenu total / Nombre de visites

                Une fois les formules saisies, vérifiez que le taux de conversion global (ligne TOTAUX) se situe entre le taux le plus bas et le taux le plus haut des 3 mois - c'est un bon indicateur de cohérence des calculs. Cliquez ensuite sur "Marquer comme terminé".
                """, """
                {"cells":{
                  "A1":{"raw":"Mois","bold":true},
                  "B1":{"raw":"Trafic organique","bold":true},
                  "C1":{"raw":"Mots-clés en 1ère page","bold":true},
                  "D1":{"raw":"Conversions","bold":true},
                  "E1":{"raw":"Taux de conversion","bold":true},
                  "F1":{"raw":"Revenu généré (FCFA)","bold":true},
                  "G1":{"raw":"Revenu moyen par visite","bold":true},
                  "A2":{"raw":"Mois 1"},
                  "B2":{"raw":"950"},
                  "C2":{"raw":"12"},
                  "D2":{"raw":"19"},
                  "F2":{"raw":"380000","format":"currency"},
                  "A3":{"raw":"Mois 2"},
                  "B3":{"raw":"1800"},
                  "C3":{"raw":"34"},
                  "D3":{"raw":"45"},
                  "F3":{"raw":"900000","format":"currency"},
                  "A4":{"raw":"Mois 3"},
                  "B4":{"raw":"2900"},
                  "C4":{"raw":"68"},
                  "D4":{"raw":"93"},
                  "F4":{"raw":"1800000","format":"currency"},
                  "A5":{"raw":"TOTAUX","bold":true},
                  "B5":{"raw":"=SOMME(B2:B4)","bold":true},
                  "D5":{"raw":"=SOMME(D2:D4)","bold":true},
                  "F5":{"raw":"=SOMME(F2:F4)","bold":true,"format":"currency"}
                }}
                """),
            new LessonSpec("Quiz : SEO et Growth Marketing", Lesson.LessonType.QUIZ, 15, """
                Ce quiz vous permet de vérifier votre compréhension des notions essentielles abordées dans ce module :

                - Le fonctionnement des moteurs de recherche : crawl, indexation et classement
                - Les trois piliers du SEO : SEO technique, contenu et netlinking, et leurs priorités selon le stade de vie d'un site
                - La méthode de recherche de mots-clés (intentions de recherche, longue traîne) et les bonnes pratiques d'optimisation on-page (Title, méta-description, Hn, URL, balises alt)
                - Les facteurs de succès d'une refonte SEO : optimisation technique, enrichissement du contenu, netlinking de qualité
                - Le calcul et l'interprétation du taux de conversion organique et du revenu moyen par visite

                Avant de répondre, relisez si besoin les leçons précédentes : "Comment fonctionnent les moteurs de recherche : crawl, index, classement", "Les 3 piliers du SEO : technique, contenu et netlinking", "Recherche de mots-clés et optimisation on-page" et "Étude de cas : la refonte SEO d'une boutique en ligne de mode".

                Conseil : pour les questions techniques, associez toujours chaque bonne pratique à l'un des trois piliers du SEO (technique, contenu, netlinking) afin de structurer votre raisonnement.

                Bonne chance !
                """)
        );
    }

    private List<LessonSpec> marketingCours3Lessons() {
        return List.of(
            new LessonSpec("Panorama des réseaux sociaux et de leurs audiences", Lesson.LessonType.VIDEO, 22, """
                Chaque réseau social possède sa propre culture, son propre format de contenu privilégié et une audience aux caractéristiques spécifiques. Choisir les bons réseaux pour sa marque est une décision stratégique qui doit se baser sur la présence réelle de sa cible, et non sur la popularité générale de la plateforme.

                FACEBOOK
                - Audience très large et intergénérationnelle, forte pénétration en Afrique de l'Ouest
                - Formats privilégiés : publications texte/image, vidéos, groupes communautaires, Marketplace
                - Usage marketing : notoriété, service client, vente directe via Marketplace et messagerie

                INSTAGRAM
                - Audience plus jeune (18-35 ans), forte dimension visuelle et esthétique
                - Formats privilégiés : photos soignées, Reels (vidéos courtes), Stories éphémères
                - Usage marketing : image de marque, mode, beauté, restauration, produits visuellement attractifs

                WHATSAPP
                - Messagerie la plus utilisée en Afrique francophone, taux d'ouverture des messages très élevé
                - Usage marketing : relation client individualisée, WhatsApp Business pour catalogues et commandes, diffusion de listes de diffusion

                TIKTOK
                - Audience jeune (16-30 ans), forte croissance en Afrique de l'Ouest
                - Formats privilégiés : vidéos courtes et divertissantes, tendances et défis (« challenges »)
                - Usage marketing : notoriété auprès des jeunes, contenu créatif viral, collaboration avec des créateurs de contenu (influenceurs)

                LINKEDIN
                - Audience professionnelle (cadres, entrepreneurs, chercheurs d'emploi)
                - Usage marketing : marketing B2B (entreprise à entreprise), recrutement, marque employeur, contenu d'expertise

                MÉTHODE POUR CHOISIR SES RÉSEAUX PRIORITAIRES
                1. Identifier où se trouve réellement le persona d'acheteur défini précédemment
                2. Analyser les réseaux utilisés par la concurrence directe et leur niveau d'engagement
                3. Évaluer sa capacité réelle à produire du contenu adapté à chaque plateforme (une vidéo TikTok ne se produit pas comme une publication LinkedIn)
                4. Concentrer ses efforts sur 2 ou 3 réseaux maîtrisés plutôt que de se disperser sur tous les canaux

                Dans les leçons suivantes, nous verrons comment organiser sa production de contenu avec un calendrier éditorial, puis comment lancer et piloter des campagnes publicitaires payantes sur ces plateformes.
                """),
            new LessonSpec("Construire un calendrier éditorial et une ligne éditoriale de marque", Lesson.LessonType.PDF, 27, """
                Publier du contenu sur les réseaux sociaux sans planification conduit rapidement à l'essoufflement et à l'incohérence. Le calendrier éditorial est l'outil qui structure la production et la diffusion de contenu dans la durée.

                LA LIGNE ÉDITORIALE : DÉFINIR L'IDENTITÉ DE LA MARQUE
                Avant de planifier des publications, il faut définir la ligne éditoriale, c'est-à-dire les règles qui garantissent la cohérence de la communication :
                - Le ton de voix : formel, humoristique, inspirant, proche du client... (constant sur toutes les publications)
                - Les valeurs et messages clés à transmettre régulièrement
                - La charte visuelle : couleurs, typographies, styles de photos/vidéos
                - Ce que la marque ne fera jamais (sujets ou tons à éviter)

                LES PILIERS DE CONTENU
                Une bonne pratique consiste à définir 3 à 5 « piliers de contenu » qui structurent toutes les publications, par exemple pour une marque de cosmétiques :
                - Pilier « Éducation » : conseils d'utilisation des produits
                - Pilier « Coulisses » : fabrication, équipe, valeurs de l'entreprise
                - Pilier « Preuve sociale » : témoignages et avis clients
                - Pilier « Promotion » : offres, nouveautés, lancements
                - Pilier « Engagement » : sondages, questions, jeux-concours

                CONSTRUIRE LE CALENDRIER ÉDITORIAL
                Le calendrier éditorial planifie, pour chaque publication future :
                - La date et l'heure de publication (en tenant compte des moments où l'audience est la plus active)
                - Le réseau social concerné
                - Le pilier de contenu et le format (image, vidéo, carrousel, Story...)
                - Le texte d'accompagnement et les hashtags
                - L'objectif de la publication (notoriété, engagement, conversion) et l'appel à l'action

                FRÉQUENCE DE PUBLICATION RECOMMANDÉE (à titre indicatif)
                - Facebook : 3 à 5 publications par semaine
                - Instagram (fil + Stories) : 3 à 4 publications au fil, Stories quotidiennes
                - TikTok : 3 à 7 vidéos par semaine (la régularité prime sur la perfection)
                - LinkedIn : 2 à 3 publications par semaine

                BONNES PRATIQUES
                - Préparer le calendrier au moins 2 à 4 semaines à l'avance, tout en gardant de la flexibilité pour l'actualité
                - Varier les formats et les piliers pour éviter la monotonie
                - Analyser chaque semaine les publications les plus performantes pour ajuster le calendrier suivant

                Un calendrier éditorial bien construit garantit une présence régulière, cohérente et alignée sur les objectifs marketing de la marque.
                """),
            new LessonSpec("Créer et piloter une campagne Facebook et Instagram Ads", Lesson.LessonType.VIDEO, 32, """
                La publicité sur Facebook et Instagram (gérée via le Gestionnaire de publicités Meta) permet de toucher une audience précise moyennant un budget maîtrisé. Comprendre sa structure et sa logique de pilotage est essentiel pour éviter de gaspiller son budget.

                LA STRUCTURE D'UNE CAMPAGNE META ADS
                Toute campagne s'organise en 3 niveaux hiérarchiques :
                1. La campagne : définit l'objectif marketing global (notoriété, trafic, engagement, génération de leads, ventes)
                2. L'ensemble de publicités (ad set) : définit le ciblage (audience), le budget, le calendrier de diffusion et l'emplacement (fil Facebook, Stories, Reels, Audience Network)
                3. La publicité (ad) : le contenu visuel et textuel effectivement montré à l'audience (image, vidéo, carrousel)

                LE CIBLAGE DE L'AUDIENCE
                - Ciblage démographique : âge, sexe, localisation, langue
                - Ciblage par centres d'intérêt : pages suivies, comportements d'achat déclarés
                - Audience personnalisée : basée sur vos propres données (liste de clients, visiteurs du site web)
                - Audience similaire (« lookalike ») : des profils qui ressemblent à vos meilleurs clients existants, générée automatiquement par l'algorithme Meta

                CHOISIR SON OBJECTIF ET SA STRATÉGIE D'ENCHÈRES
                Meta Ads propose différents objectifs selon l'étape du tunnel de conversion visée. Pour une campagne de notoriété, on optimisera pour la couverture (reach). Pour une campagne de conversion, on optimisera directement pour l'action souhaitée (achat, formulaire rempli), à condition que le pixel de suivi soit correctement installé sur le site.

                MÉTHODE DE PILOTAGE D'UNE CAMPAGNE
                1. Démarrer avec un budget test modeste (par exemple 3 000 à 5 000 FCFA/jour) sur 3-5 jours pour identifier les meilleures combinaisons audience/visuel
                2. Tester plusieurs versions du visuel et du texte (A/B test) pour identifier ce qui fonctionne le mieux
                3. Surveiller quotidiennement le coût par résultat et le désactiver si un ensemble de publicités sous-performe
                4. Une fois la meilleure combinaison identifiée, augmenter progressivement le budget (jamais brutalement, au risque de perturber l'algorithme d'optimisation)
                5. Renouveler régulièrement les visuels pour éviter la « fatigue publicitaire » (baisse de performance quand la même publicité est vue trop souvent par la même audience)

                Le pilotage rigoureux d'une campagne publicitaire, avec des tests réguliers et une lecture attentive des indicateurs, permet de maximiser le retour sur chaque franc investi.
                """),
            new LessonSpec("Étude de cas : le lancement publicitaire d'une marque de cosmétiques bio en ligne", Lesson.LessonType.PDF, 32, """
                ÉTUDE DE CAS : NATURA BEAUTY, MARQUE DE COSMÉTIQUES BIO

                Natura Beauty est une marque ivoirienne de cosmétiques à base d'ingrédients naturels (karité, huile de coco, savon noir) vendue exclusivement en ligne. Avant sa campagne publicitaire, la marque ne réalisait des ventes qu'auprès de son entourage proche, soit environ 25 commandes par mois.

                OBJECTIF DE LA CAMPAGNE
                Générer 150 commandes par mois via la publicité Facebook et Instagram, avec un budget mensuel de 250 000 FCFA, tout en maintenant un coût d'acquisition client (CAC) inférieur à 3 000 FCFA.

                STRUCTURE DE LA CAMPAGNE
                - Campagne 1 (Notoriété) : vidéos courtes montrant la fabrication artisanale des produits, ciblant les femmes de 20-45 ans en Côte d'Ivoire intéressées par le bio et le naturel - budget 60 000 FCFA
                - Campagne 2 (Trafic/Engagement) : carrousels présentant la gamme de produits avec témoignages clients, ciblant les personnes ayant interagi avec la campagne 1 - budget 90 000 FCFA
                - Campagne 3 (Conversion) : publicités avec offre de lancement (-15 % première commande), ciblant une audience similaire (lookalike) aux clientes existantes - budget 100 000 FCFA

                PHASE DE TEST (SEMAINE 1)
                - 4 visuels différents testés simultanément avec un petit budget (5 000 FCFA/jour chacun)
                - Le visuel « avant/après utilisation du savon noir » obtient un taux de clic 3 fois supérieur aux autres

                AJUSTEMENTS (SEMAINES 2 À 4)
                - Réallocation de 70 % du budget vers le visuel gagnant
                - Élargissement de l'audience similaire de 1 % à 3 % pour toucher davantage de personnes
                - Renouvellement du visuel en semaine 4 pour éviter la fatigue publicitaire (baisse du taux de clic observée en fin de semaine 3)

                RÉSULTATS APRÈS 1 MOIS
                - Nombre de commandes : 178 (objectif dépassé)
                - Coût d'acquisition client moyen : 2 400 FCFA
                - Retour sur les dépenses publicitaires (ROAS) : 4,2 (chaque 1 FCFA investi a généré 4,2 FCFA de revenu)
                - Taux de conversion des visiteurs venant des publicités : 5,8 %

                LEÇONS À RETENIR
                - Tester plusieurs visuels à petite échelle avant d'engager le budget principal réduit fortement le risque
                - La structure en 3 campagnes (notoriété, considération, conversion) permet de guider progressivement le prospect jusqu'à l'achat
                - Le renouvellement régulier des visuels est indispensable pour maintenir la performance dans la durée
                - Le ROAS est l'indicateur clé pour juger de la rentabilité réelle d'une campagne publicitaire
                """),
            new LessonSpec("Pratique : suivi budgétaire multi-canal et calcul du ROAS (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 22, """
                Mettez en pratique le suivi d'un budget publicitaire réparti sur plusieurs canaux et le calcul du ROAS (Return On Ad Spend - retour sur les dépenses publicitaires).

                Un tableau "Suivi budgétaire multi-canal" est déjà prêt avec les colonnes Canal, Budget alloué (FCFA), Dépense réelle (FCFA), Revenu généré (FCFA), ROAS et Écart budgétaire, ainsi qu'une ligne TOTAUX (ligne 5) qui additionne automatiquement le Budget alloué, la Dépense réelle et le Revenu généré grâce à la formule =SOMME(...).

                Les données de 3 canaux (Facebook Ads, Instagram Ads, TikTok Ads) sont déjà saisies dans les colonnes Budget alloué, Dépense réelle et Revenu généré (lignes 2 à 4).

                Votre mission :
                1. Dans la colonne ROAS (colonne E), calculez pour chaque canal le retour sur les dépenses publicitaires avec la formule : Revenu généré / Dépense réelle
                2. Dans la colonne Écart budgétaire (colonne F), calculez la différence entre le budget alloué et la dépense réelle avec la formule : Budget alloué - Dépense réelle
                3. Complétez la ligne TOTAUX pour le ROAS global (Revenu total / Dépense totale) et l'écart budgétaire global
                4. Identifiez le canal ayant le meilleur ROAS : c'est celui vers lequel il serait pertinent de réallouer une partie du budget du canal le moins performant

                Rappel des formules :
                - ROAS = Revenu généré par la publicité / Dépense publicitaire
                - Écart budgétaire = Budget alloué - Dépense réelle (un écart positif signifie un budget non consommé, un écart négatif un dépassement)

                Une fois toutes les formules saisies, vérifiez que le ROAS global (ligne TOTAUX) se situe bien entre le ROAS le plus faible et le plus élevé des 3 canaux. Cliquez ensuite sur "Marquer comme terminé".
                """, """
                {"cells":{
                  "A1":{"raw":"Canal","bold":true},
                  "B1":{"raw":"Budget alloué (FCFA)","bold":true},
                  "C1":{"raw":"Dépense réelle (FCFA)","bold":true},
                  "D1":{"raw":"Revenu généré (FCFA)","bold":true},
                  "E1":{"raw":"ROAS","bold":true},
                  "F1":{"raw":"Écart budgétaire","bold":true},
                  "A2":{"raw":"Facebook Ads"},
                  "B2":{"raw":"100000","format":"currency"},
                  "C2":{"raw":"95000","format":"currency"},
                  "D2":{"raw":"399000","format":"currency"},
                  "A3":{"raw":"Instagram Ads"},
                  "B3":{"raw":"90000","format":"currency"},
                  "C3":{"raw":"88000","format":"currency"},
                  "D3":{"raw":"264000","format":"currency"},
                  "A4":{"raw":"TikTok Ads"},
                  "B4":{"raw":"60000","format":"currency"},
                  "C4":{"raw":"62000","format":"currency"},
                  "D4":{"raw":"148800","format":"currency"},
                  "A5":{"raw":"TOTAUX","bold":true},
                  "B5":{"raw":"=SOMME(B2:B4)","bold":true,"format":"currency"},
                  "C5":{"raw":"=SOMME(C2:C4)","bold":true,"format":"currency"},
                  "D5":{"raw":"=SOMME(D2:D4)","bold":true,"format":"currency"}
                }}
                """),
            new LessonSpec("Quiz : Réseaux sociaux et publicité en ligne", Lesson.LessonType.QUIZ, 15, """
                Ce quiz vous permet de vérifier votre compréhension des notions essentielles abordées dans ce module :

                - Les spécificités de chaque réseau social (Facebook, Instagram, WhatsApp, TikTok, LinkedIn) et les critères pour choisir les canaux prioritaires de sa marque
                - La construction d'une ligne éditoriale (ton, valeurs, charte visuelle) et d'un calendrier éditorial structuré autour de piliers de contenu
                - La structure hiérarchique d'une campagne Meta Ads (campagne, ensemble de publicités, publicité) et les options de ciblage disponibles
                - La méthode de test et de pilotage d'une campagne publicitaire (budget test, A/B test, fatigue publicitaire)
                - Le calcul et l'interprétation du ROAS (retour sur les dépenses publicitaires) et de l'écart budgétaire

                Avant de répondre, relisez si besoin les leçons précédentes : "Panorama des réseaux sociaux et de leurs audiences", "Construire un calendrier éditorial et une ligne éditoriale de marque", "Créer et piloter une campagne Facebook et Instagram Ads" et "Étude de cas : le lancement publicitaire d'une marque de cosmétiques bio en ligne".

                Conseil : pour les questions de calcul, identifiez d'abord le revenu généré et la dépense engagée, puis appliquez la formule du ROAS (Revenu / Dépense) avant de comparer les canaux entre eux.

                Bonne chance !
                """)
        );
    }

    // ===================== Développement Personnel =====================

    private List<LessonSpec> devPersoCours1Lessons() {
        return List.of(
            new LessonSpec("Qu'est-ce que le développement personnel ? Poser les bases d'un changement durable", Lesson.LessonType.VIDEO, 25, """
                Le développement personnel est une démarche volontaire et continue par laquelle une personne apprend à mieux se connaître, développe son potentiel et agit concrètement pour améliorer sa vie personnelle et professionnelle. Ce n'est pas une mode passagère ni une collection de formules toutes faites : c'est un ensemble de connaissances et d'outils issus de la psychologie positive, des sciences de l'éducation des adultes et du coaching, que chacun peut s'approprier à son rythme.

                Pourquoi s'engager dans une démarche de développement personnel ?
                - Mieux se connaître : identifier ses forces, ses limites, ses valeurs et ce qui donne du sens à sa vie
                - Gagner en autonomie et en confiance pour prendre des décisions importantes
                - Améliorer ses relations familiales, sociales et professionnelles
                - Progresser dans sa carrière ou ses études grâce à une meilleure organisation et une meilleure gestion de soi
                - Renforcer son bien-être général et sa capacité à traverser les difficultés

                Deux notions essentielles à connaître dès le départ :

                1. L'état d'esprit de croissance (Carol Dweck) : une personne avec un état d'esprit fixe pense que ses capacités sont figées ("je ne suis pas doué pour ça"). Une personne avec un état d'esprit de croissance pense que ses compétences se développent par l'effort et l'apprentissage ("je ne sais pas encore le faire"). Cette seconde posture est la clé de tout progrès durable.

                2. Le lieu de contrôle (locus of control) : une personne avec un lieu de contrôle interne pense qu'elle a une influence réelle sur ce qui lui arrive, tandis qu'une personne avec un lieu de contrôle externe attribue ses résultats à la chance, aux autres ou aux circonstances. Renforcer son lieu de contrôle interne est l'un des premiers objectifs de ce module.

                Dans les leçons suivantes, vous allez découvrir la roue de la vie pour évaluer votre équilibre actuel, apprendre à clarifier vos valeurs et à fixer des objectifs SMART, puis construire votre propre plan de développement personnel.
                """),
            new LessonSpec("La roue de la vie : cartographier les huit piliers de son équilibre personnel", Lesson.LessonType.PDF, 28, """
                La roue de la vie est un outil visuel simple et puissant utilisé en coaching pour évaluer, en un coup d'œil, le niveau de satisfaction d'une personne dans les grands domaines de son existence. Elle permet d'identifier rapidement les zones d'équilibre et les zones qui méritent une attention prioritaire.

                LES HUIT PILIERS CLASSIQUES DE LA ROUE DE LA VIE
                - Santé & Énergie : sommeil, alimentation, activité physique, niveau d'énergie général
                - Finances : revenus, épargne, gestion du budget, sérénité financière
                - Carrière & Travail : satisfaction professionnelle, progression, reconnaissance
                - Relations & Famille : qualité des liens avec le conjoint, les enfants, les proches
                - Développement personnel : apprentissage, lecture, formation, croissance intérieure
                - Loisirs & Détente : temps libre, plaisir, activités qui ressourcent
                - Environnement (cadre de vie) : logement, quartier, organisation matérielle du quotidien
                - Contribution & Sens : engagement communautaire, spiritualité, sentiment d'utilité

                MÉTHODE D'UTILISATION
                1. Pour chaque domaine, attribuez-vous une note de 0 à 10 correspondant à votre niveau de satisfaction actuel (0 = très insatisfait, 10 = pleinement satisfait)
                2. Reliez les points pour dessiner votre "roue" : plus elle est ronde et régulière, plus votre vie est équilibrée ; plus elle est irrégulière, plus certains domaines sont négligés
                3. Identifiez les deux ou trois domaines avec les scores les plus bas : ce sont vos priorités de travail pour les prochains mois
                4. Pour chaque domaine prioritaire, notez une action concrète que vous pourriez entreprendre dans les 30 prochains jours

                EXEMPLE D'INTERPRÉTATION
                Une personne qui note 8 en Carrière, 8 en Finances, mais seulement 3 en Santé et 2 en Relations montre un déséquilibre fréquent chez les professionnels très investis dans leur travail : la réussite professionnelle se construit au détriment du corps et des liens affectifs. La roue de la vie permet de rendre ce déséquilibre visible avant qu'il ne provoque un épuisement ou une rupture.

                Dans la prochaine leçon, nous verrons comment transformer les constats de votre roue de la vie en objectifs clairs et atteignables grâce à la clarification des valeurs et à la méthode SMART.
                """),
            new LessonSpec("Clarifier ses valeurs et transformer ses aspirations en objectifs SMART", Lesson.LessonType.VIDEO, 30, """
                Une fois la roue de la vie établie, il faut passer à l'action. Mais avant de fixer des objectifs, il est essentiel de clarifier ses valeurs : ce sont elles qui donnent du sens et de la motivation durable à un objectif.

                CLARIFIER SES VALEURS
                Une valeur est un principe qui compte profondément pour vous et qui guide vos choix (par exemple : la famille, la liberté, l'apprentissage, la sécurité, l'honnêteté, la réussite, la solidarité). Pour identifier vos valeurs :
                1. Listez librement une vingtaine de mots qui représentent ce qui compte pour vous
                2. Regroupez les mots proches en familles
                3. Retenez vos 5 valeurs prioritaires
                4. Pour chacune, demandez-vous : "est-ce que ma vie actuelle respecte cette valeur ?"

                Un objectif aligné avec vos valeurs est beaucoup plus facile à tenir dans la durée qu'un objectif imposé de l'extérieur ou choisi par comparaison avec les autres.

                LA MÉTHODE SMART
                Une fois les priorités identifiées grâce à la roue de la vie et aux valeurs, il faut les traduire en objectifs SMART :
                - Spécifique : l'objectif est précis et clair, sans ambiguïté
                - Mesurable : on peut vérifier objectivement s'il est atteint (un chiffre, un fait observable)
                - Atteignable : il est ambitieux mais réaliste compte tenu de vos ressources actuelles
                - Réaliste (pertinent) : il a du sens par rapport à vos valeurs et à votre situation
                - Temporellement défini : il a une date limite claire

                EXEMPLE DE TRANSFORMATION
                Objectif vague : "Je veux être en meilleure santé."
                Objectif SMART : "Je marche 30 minutes, 4 fois par semaine, pendant les 3 prochains mois, et je perds 3 kilos d'ici la fin du trimestre."

                Autre exemple :
                Objectif vague : "Je veux progresser dans mon travail."
                Objectif SMART : "D'ici 6 mois, je termine une certification en gestion de projet et je propose une réunion mensuelle avec mon responsable pour faire le point sur mon évolution."

                Dans la prochaine leçon, nous allons appliquer toute cette méthode à un cas concret : celui d'Aïssatou, qui construit son plan de développement personnel sur 12 mois.
                """),
            new LessonSpec("Étude de cas : le plan de développement personnel de Aïssatou sur 12 mois", Lesson.LessonType.PDF, 32, """
                ÉTUDE DE CAS : AÏSSATOU, 29 ANS, COMPTABLE

                Aïssatou travaille dans un cabinet comptable depuis 5 ans. Elle a réalisé sa roue de la vie et obtient les scores suivants sur 10 : Carrière 8, Finances 7, Environnement 6, Développement personnel 4, Contribution & Sens 4, Relations & Famille 3, Loisirs & Détente 3, Santé & Énergie 2.

                ÉTAPE 1 : IDENTIFIER LES PRIORITÉS
                Les trois scores les plus bas concernent la Santé, les Loisirs et les Relations. Aïssatou constate qu'elle passe la majorité de son temps et de son énergie sur son travail, au détriment de son corps et de ses proches.

                ÉTAPE 2 : CLARIFIER LES VALEURS
                Après l'exercice de clarification, Aïssatou identifie ses 5 valeurs prioritaires : la santé, la famille, l'apprentissage, l'honnêteté et la réussite. Elle réalise que sa vie actuelle respecte bien la réussite et l'apprentissage, mais néglige fortement la santé et la famille.

                ÉTAPE 3 : FIXER TROIS OBJECTIFS SMART
                1. Santé : "Je pratique une activité physique 3 fois par semaine (30 minutes de marche rapide) pendant 6 mois, et je fais un bilan médical de contrôle d'ici la fin du trimestre."
                2. Relations & Famille : "Je consacre un dimanche par mois exclusivement à ma famille, sans téléphone professionnel, pendant les 12 prochains mois."
                3. Loisirs & Détente : "Je m'inscris à un cours de couture le samedi matin dès le mois prochain et j'y assiste au moins 3 fois par mois."

                ÉTAPE 4 : PLAN D'ACTION ET JALONS MENSUELS
                Mois 1-2 : mise en place des nouvelles habitudes (marche, dimanche en famille, inscription au cours)
                Mois 3 : bilan médical de mi-parcours et ajustement si nécessaire
                Mois 6 : évaluation à mi-parcours de la roue de la vie ; les scores de Santé et Relations devraient progresser d'au moins 2 points
                Mois 12 : nouvelle roue de la vie complète, bilan de l'année et définition de nouveaux objectifs

                TRAVAIL À FAIRE
                En vous inspirant de la démarche d'Aïssatou : identifiez vos deux domaines les plus faibles sur votre propre roue de la vie, rédigez un objectif SMART pour chacun, et prévoyez au moins deux jalons de suivi dans l'année.
                """),
            new LessonSpec("Mon bilan de vie : construire sa roue de la vie chiffrée (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 20, """
                Mettez en pratique la roue de la vie directement dans le tableur intégré.

                Un tableau est déjà prêt avec les 8 domaines de vie, une colonne "Score actuel" (satisfaction sur 10), une colonne "Priorité" (poids de 1 à 5 selon l'importance que vous accordez à ce domaine en ce moment) et une colonne "Score pondéré" qui multiplie automatiquement le score par le poids grâce à la formule =B×C.

                Votre mission :
                1. Ajustez les valeurs des colonnes "Score actuel" (B) et "Priorité" (C) pour qu'elles reflètent votre propre situation actuelle
                2. Observez comment la colonne "Score pondéré" (D) se met à jour automatiquement
                3. Vérifiez la ligne TOTAUX (ligne 13) : elle calcule la moyenne de vos scores de satisfaction (cellule B13, formule =MOYENNE(...)) et la somme totale de vos scores pondérés (cellule D13, formule =SOMME(...))
                4. Identifiez les deux domaines avec le score le plus bas : ce sont vos priorités d'action pour le mois prochain

                Une fois le tableau complété, comparez votre moyenne globale (B13) à 7/10 : en dessous de ce seuil, considérez qu'il s'agit d'un signal pour agir rapidement sur les domaines concernés. Cliquez ensuite sur "Marquer comme terminé".
                """, """
                {"cells":{
                  "A1":{"raw":"MA ROUE DE LA VIE - BILAN PERSONNEL","bold":true},
                  "A3":{"raw":"Domaine de vie","bold":true},
                  "B3":{"raw":"Score actuel (/10)","bold":true},
                  "C3":{"raw":"Priorité (poids /5)","bold":true},
                  "D3":{"raw":"Score pondéré (Score × Poids)","bold":true},
                  "A4":{"raw":"Santé & Énergie"},
                  "B4":{"raw":"6","format":"number"},
                  "C4":{"raw":"5","format":"number"},
                  "D4":{"raw":"=B4*C4","format":"number"},
                  "A5":{"raw":"Finances"},
                  "B5":{"raw":"5","format":"number"},
                  "C5":{"raw":"4","format":"number"},
                  "D5":{"raw":"=B5*C5","format":"number"},
                  "A6":{"raw":"Carrière & Travail"},
                  "B6":{"raw":"7","format":"number"},
                  "C6":{"raw":"5","format":"number"},
                  "D6":{"raw":"=B6*C6","format":"number"},
                  "A7":{"raw":"Relations & Famille"},
                  "B7":{"raw":"8","format":"number"},
                  "C7":{"raw":"4","format":"number"},
                  "D7":{"raw":"=B7*C7","format":"number"},
                  "A8":{"raw":"Développement personnel"},
                  "B8":{"raw":"4","format":"number"},
                  "C8":{"raw":"3","format":"number"},
                  "D8":{"raw":"=B8*C8","format":"number"},
                  "A9":{"raw":"Loisirs & Détente"},
                  "B9":{"raw":"3","format":"number"},
                  "C9":{"raw":"2","format":"number"},
                  "D9":{"raw":"=B9*C9","format":"number"},
                  "A10":{"raw":"Environnement (cadre de vie)"},
                  "B10":{"raw":"7","format":"number"},
                  "C10":{"raw":"3","format":"number"},
                  "D10":{"raw":"=B10*C10","format":"number"},
                  "A11":{"raw":"Contribution & Sens"},
                  "B11":{"raw":"5","format":"number"},
                  "C11":{"raw":"3","format":"number"},
                  "D11":{"raw":"=B11*C11","format":"number"},
                  "A13":{"raw":"TOTAUX","bold":true},
                  "B13":{"raw":"=MOYENNE(B4:B11)","bold":true,"format":"number"},
                  "D13":{"raw":"=SOMME(D4:D11)","bold":true,"format":"number"}
                }}
                """),
            new LessonSpec("Quiz : Les fondations du développement personnel", Lesson.LessonType.QUIZ, 15, """
                Ce quiz vous permet de vérifier votre compréhension des notions essentielles abordées dans ce module :

                - La définition du développement personnel et la différence entre état d'esprit fixe et état d'esprit de croissance
                - Le lieu de contrôle interne et son importance pour agir sur sa vie
                - Les huit piliers de la roue de la vie et la méthode pour l'utiliser
                - La clarification des valeurs personnelles comme fondement de la motivation durable
                - La méthode SMART pour transformer une aspiration vague en objectif clair et atteignable
                - L'application de ces outils à un cas concret de construction d'un plan de développement personnel sur 12 mois

                Avant de répondre, relisez si besoin les leçons précédentes : "Qu'est-ce que le développement personnel ? Poser les bases d'un changement durable", "La roue de la vie : cartographier les huit piliers de son équilibre personnel", "Clarifier ses valeurs et transformer ses aspirations en objectifs SMART" et "Étude de cas : le plan de développement personnel de Aïssatou sur 12 mois".

                Conseil : pour chaque question, demandez-vous d'abord à quel pilier de la roue de la vie ou à quelle étape de la méthode SMART elle se rapporte.

                Bonne chance !
                """)
        );
    }

    private List<LessonSpec> devPersoCours2Lessons() {
        return List.of(
            new LessonSpec("Comprendre son rapport au temps : chronotype, énergie et fuites de temps", Lesson.LessonType.VIDEO, 25, """
                Bien gérer son temps ne consiste pas seulement à remplir un agenda : c'est avant tout une question de gestion de son énergie et de ses priorités. Cette leçon pose les bases indispensables avant d'aborder les outils pratiques des prochaines leçons.

                LE CHRONOTYPE : CONNAÎTRE SON HORLOGE BIOLOGIQUE
                Chaque personne a des périodes de la journée où son énergie et sa concentration sont naturellement plus élevées. On distingue généralement :
                - Les profils du matin : pic d'énergie tôt le matin, baisse en fin de journée
                - Les profils du soir : montée en puissance progressive, pic d'énergie en fin d'après-midi ou en soirée
                - Les profils intermédiaires : énergie plus stable, avec un léger creux après le repas de midi

                Identifier son chronotype permet de placer les tâches qui demandent le plus de concentration (rédaction, analyse, prise de décision) pendant ses heures de haute énergie, et de réserver les tâches administratives ou répétitives pour les heures de moindre énergie.

                LES PRINCIPALES FUITES DE TEMPS
                - Les réseaux sociaux et notifications non filtrées
                - Les réunions mal préparées ou sans ordre du jour
                - La procrastination face aux tâches complexes ou désagréables
                - Le multitâche, qui donne l'illusion de gagner du temps mais qui augmente en réalité les erreurs et la fatigue mentale
                - L'incapacité à dire non à des sollicitations non prioritaires

                LA LOI DE PARKINSON
                Cette loi énonce que "le travail s'étale de façon à occuper le temps disponible pour son achèvement". Concrètement, une tâche à laquelle on accorde 3 heures prendra 3 heures, même si elle pouvait être faite en 1 heure. Se fixer des délais plus courts et réalistes est donc un levier puissant de productivité.

                Dans les prochaines leçons, nous découvrirons des outils concrets pour prioriser (matrice d'Eisenhower), pour se concentrer (Pomodoro, time blocking) et pour organiser une semaine complète (méthode GTD).
                """),
            new LessonSpec("La matrice d'Eisenhower et les lois de la priorisation (Pareto, Parkinson, Illich)", Lesson.LessonType.PDF, 28, """
                Prioriser efficacement est la compétence centrale de la gestion du temps. Cette leçon présente l'outil de référence, la matrice d'Eisenhower, ainsi que plusieurs lois qui éclairent la façon dont nous utilisons réellement notre temps.

                LA MATRICE D'EISENHOWER
                Cette matrice classe les tâches selon deux critères : leur urgence et leur importance, en quatre quadrants :
                - Quadrant 1 (Urgent + Important) : "à faire immédiatement" — crises, échéances imminentes, problèmes urgents. Exemple : répondre à un client dont la commande doit partir aujourd'hui.
                - Quadrant 2 (Important, non urgent) : "à planifier" — formation, prévention, relations, développement personnel, planification stratégique. Exemple : préparer un plan d'action trimestriel.
                - Quadrant 3 (Urgent, peu important) : "à déléguer" — sollicitations, certains appels et e-mails, interruptions diverses. Exemple : répondre à une demande d'information qu'un collègue pourrait traiter.
                - Quadrant 4 (Ni urgent ni important) : "à éliminer ou limiter" — distractions, activités qui ne servent aucun objectif. Exemple : défiler sans but les réseaux sociaux.

                La clé de la productivité durable est d'investir chaque semaine davantage de temps dans le Quadrant 2 : c'est lui qui prévient les urgences du Quadrant 1 à long terme.

                LES LOIS DU TEMPS À CONNAÎTRE
                - Loi de Pareto (80/20) : environ 80 % des résultats proviennent de 20 % des actions. Il faut identifier ces 20 % d'actions à fort impact et leur accorder la priorité.
                - Loi de Parkinson : une tâche occupe tout le temps qu'on lui accorde. Fixer des délais plus courts augmente souvent l'efficacité sans nuire à la qualité.
                - Loi d'Illich (rendement décroissant) : au-delà d'un certain seuil de temps continu passé sur une tâche, l'efficacité diminue fortement ; des pauses régulières sont nécessaires.
                - Loi de Laborit : nous avons naturellement tendance à faire d'abord ce qui est agréable et à repousser ce qui est difficile, même si c'est prioritaire ; en avoir conscience permet de corriger ce biais.

                MÉTHODE PRATIQUE
                Chaque soir ou chaque début de semaine, classez votre liste de tâches dans les quatre quadrants avant de commencer à agir : cela évite de passer la journée en mode "réaction" et permet de rester concentré sur ce qui compte vraiment.
                """),
            new LessonSpec("Techniques de concentration : Pomodoro, time blocking et la règle des deux minutes", Lesson.LessonType.VIDEO, 30, """
                Après avoir appris à prioriser, il faut apprendre à protéger sa concentration au quotidien. Cette leçon présente trois techniques complémentaires, simples à mettre en œuvre immédiatement.

                LA TECHNIQUE POMODORO
                Développée par Francesco Cirillo, cette méthode découpe le travail en cycles courts et intenses :
                1. Choisissez une tâche unique et éliminez les distractions (téléphone en mode avion, notifications coupées)
                2. Travaillez pendant 25 minutes sans interruption, minutée
                3. Prenez une pause courte de 5 minutes
                4. Après 4 cycles ("pomodoros"), prenez une pause longue de 20 à 30 minutes

                Cette technique fonctionne car elle exploite la pression positive d'une contrainte de temps courte (loi de Parkinson) tout en évitant l'épuisement grâce aux pauses régulières.

                LE TIME BLOCKING (BLOCAGE DE TEMPS)
                Il s'agit de réserver, dans son agenda, des plages horaires dédiées à des tâches ou catégories de tâches précises, plutôt que de laisser la journée se remplir au fil des sollicitations. Par exemple : 8h-10h "travail de fond sur le dossier X", 10h-10h30 "traitement des e-mails", 14h-15h "réunions". Le time blocking transforme les intentions en engagements concrets inscrits dans le calendrier.

                LA RÈGLE DES DEUX MINUTES
                Si une tâche prend moins de deux minutes à réaliser (répondre à un message court, classer un document, planifier un rendez-vous), faites-la immédiatement plutôt que de la noter pour plus tard : la noter et y revenir prendrait souvent plus de temps que de l'exécuter tout de suite.

                LE TRAITEMENT PAR LOTS (BATCHING)
                Regrouper les tâches similaires (tous les appels, tous les e-mails, toutes les courses administratives) sur des créneaux dédiés évite les coûts de changement de contexte, qui font perdre en moyenne plusieurs minutes de concentration à chaque interruption.

                Combinées, ces quatre techniques permettent de reprendre le contrôle de ses journées. La prochaine leçon les met en pratique à travers un cas complet de réorganisation d'une semaine de travail.
                """),
            new LessonSpec("Étude de cas : réorganiser une semaine surchargée avec la méthode GTD", Lesson.LessonType.PDF, 35, """
                LA MÉTHODE GTD (GETTING THINGS DONE)

                Développée par David Allen, la méthode GTD repose sur cinq étapes qui permettent de vider son esprit des tâches en suspens et de retrouver de la clarté :
                1. Collecter : noter toutes les tâches, idées et engagements dans un seul système fiable (carnet, application, boîte de réception unique), sans rien garder "dans sa tête"
                2. Clarifier : pour chaque élément collecté, décider s'il est actionnable ; si non, le classer (référence, un jour peut-être, corbeille) ; si oui, définir la prochaine action concrète
                3. Organiser : ranger chaque action dans la bonne catégorie (agenda pour ce qui a une date fixe, listes par contexte pour le reste, liste de projets pour ce qui demande plusieurs étapes)
                4. Réviser : faire chaque semaine une revue complète de toutes ses listes pour garder le système à jour et fiable
                5. S'engager : choisir, à chaque instant, la meilleure action à mener selon le contexte, le temps disponible, l'énergie et la priorité

                ÉTUDE DE CAS : LA SEMAINE DE MOUSSA, CHEF DE PROJET

                Moussa se sent débordé : sa boîte mail contient 140 messages non traités, il a des tâches personnelles et professionnelles mélangées dans sa tête, et il travaille en mode "pompier" toute la journée.

                APPLICATION DE LA MÉTHODE :
                Étape 1 (Collecter) : Moussa liste en 45 minutes tout ce qui occupe son esprit : 32 éléments au total, mélangeant tâches professionnelles, personnelles et idées de projets.
                Étape 2 (Clarifier) : il élimine 8 éléments obsolètes, classe 6 éléments en référence, et identifie une action concrète pour chacun des 18 éléments restants.
                Étape 3 (Organiser) : il crée 4 listes par contexte ("Au bureau", "Appels à passer", "En ligne", "Courses"), plus une liste de 3 projets nécessitant plusieurs étapes, et inscrit 5 rendez-vous fixes dans son agenda.
                Étape 4 (Réviser) : chaque vendredi après-midi, il bloque 30 minutes pour revoir toutes ses listes et préparer la semaine suivante.
                Étape 5 (S'engager) : chaque matin, il choisit ses 3 tâches prioritaires du jour (méthode dite "3 plus importantes") avant de consulter ses e-mails.

                RÉSULTAT : après deux semaines, Moussa constate une nette diminution de son sentiment de surcharge mentale, car il ne porte plus toutes ses tâches "dans sa tête" en permanence.

                TRAVAIL À FAIRE : réalisez votre propre étape de collecte pendant 30 minutes, puis classez les éléments obtenus selon les 5 étapes de la méthode GTD.
                """),
            new LessonSpec("Suivi hebdomadaire du temps : construire son tableau de bord (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 25, """
                Avant de pouvoir améliorer sa gestion du temps, il faut d'abord savoir précisément comment son temps est actuellement utilisé. Cet exercice vous propose de construire un tableau de bord de suivi hebdomadaire.

                Le tableur contient déjà 9 catégories d'activités courantes (Sommeil, Travail/Étude, Trajets, Repas, Famille & Relations, Sport & Santé, Loisirs & Écrans, Tâches administratives, Autre/imprévu), avec une colonne "Heures / jour (moyenne)" que vous devez ajuster selon votre réalité, ainsi que deux colonnes calculées automatiquement : "Heures / semaine" (=Heures par jour × 7) et "% du temps total" (=Heures semaine / 168, puisqu'une semaine compte 168 heures).

                Votre mission :
                1. Ajustez les valeurs de la colonne B (Heures / jour) pour refléter une semaine typique de votre vie
                2. Vérifiez que la ligne TOTAUX (ligne 14) affiche bien 24 heures en B14 (=SOMME(B4:B12)) : si le total dépasse ou est inférieur à 24, corrigez vos estimations, car une journée compte 24 heures
                3. Observez le total de la colonne D (doit afficher 100 %) : c'est la vérification que l'ensemble de votre temps est bien réparti entre les catégories
                4. Repérez la catégorie "Loisirs & Écrans" et "Sport & Santé" : comparez leur part respective. Beaucoup de personnes découvrent que le temps d'écran dépasse largement le temps consacré au sport et à la santé
                5. Identifiez une catégorie où vous aimeriez réduire le temps passé, et une autre où vous aimeriez en gagner, puis notez une action concrète pour rééquilibrer votre semaine

                Une fois les vérifications faites, cliquez sur "Marquer comme terminé".
                """, """
                {"cells":{
                  "A1":{"raw":"SUIVI HEBDOMADAIRE DU TEMPS","bold":true},
                  "A3":{"raw":"Catégorie","bold":true},
                  "B3":{"raw":"Heures / jour (moyenne)","bold":true},
                  "C3":{"raw":"Heures / semaine","bold":true},
                  "D3":{"raw":"% du temps total","bold":true},
                  "A4":{"raw":"Sommeil"},
                  "B4":{"raw":"7","format":"number"},
                  "C4":{"raw":"=B4*7","format":"number"},
                  "D4":{"raw":"=C4/168","format":"percent"},
                  "A5":{"raw":"Travail / Étude"},
                  "B5":{"raw":"8","format":"number"},
                  "C5":{"raw":"=B5*7","format":"number"},
                  "D5":{"raw":"=C5/168","format":"percent"},
                  "A6":{"raw":"Trajets"},
                  "B6":{"raw":"1.5","format":"number"},
                  "C6":{"raw":"=B6*7","format":"number"},
                  "D6":{"raw":"=C6/168","format":"percent"},
                  "A7":{"raw":"Repas"},
                  "B7":{"raw":"1.5","format":"number"},
                  "C7":{"raw":"=B7*7","format":"number"},
                  "D7":{"raw":"=C7/168","format":"percent"},
                  "A8":{"raw":"Famille & Relations"},
                  "B8":{"raw":"1.5","format":"number"},
                  "C8":{"raw":"=B8*7","format":"number"},
                  "D8":{"raw":"=C8/168","format":"percent"},
                  "A9":{"raw":"Sport & Santé"},
                  "B9":{"raw":"0.5","format":"number"},
                  "C9":{"raw":"=B9*7","format":"number"},
                  "D9":{"raw":"=C9/168","format":"percent"},
                  "A10":{"raw":"Loisirs & Écrans"},
                  "B10":{"raw":"2","format":"number"},
                  "C10":{"raw":"=B10*7","format":"number"},
                  "D10":{"raw":"=C10/168","format":"percent"},
                  "A11":{"raw":"Tâches administratives"},
                  "B11":{"raw":"0.5","format":"number"},
                  "C11":{"raw":"=B11*7","format":"number"},
                  "D11":{"raw":"=C11/168","format":"percent"},
                  "A12":{"raw":"Autre / imprévu"},
                  "B12":{"raw":"1.5","format":"number"},
                  "C12":{"raw":"=B12*7","format":"number"},
                  "D12":{"raw":"=C12/168","format":"percent"},
                  "A14":{"raw":"TOTAUX","bold":true},
                  "B14":{"raw":"=SOMME(B4:B12)","bold":true,"format":"number"},
                  "C14":{"raw":"=SOMME(C4:C12)","bold":true,"format":"number"},
                  "D14":{"raw":"=SOMME(D4:D12)","bold":true,"format":"percent"}
                }}
                """),
            new LessonSpec("Quiz : Gestion du temps et productivité", Lesson.LessonType.QUIZ, 15, """
                Ce quiz porte sur l'ensemble du module "Gestion du Temps et Productivité". Il couvre :

                - Les notions de chronotype, d'énergie et les principales fuites de temps du quotidien
                - La matrice d'Eisenhower (urgent/important) et les quatre quadrants de priorisation
                - Les lois du temps : Pareto (80/20), Parkinson, Illich et Laborit
                - Les techniques de concentration : Pomodoro, time blocking, règle des deux minutes et traitement par lots
                - Les cinq étapes de la méthode GTD (Collecter, Clarifier, Organiser, Réviser, S'engager) et leur application à un cas concret

                Avant de commencer, assurez-vous de bien savoir :
                - Classer une tâche dans le bon quadrant de la matrice d'Eisenhower
                - Expliquer pourquoi la loi de Parkinson justifie de se fixer des délais plus courts
                - Décrire le déroulement d'un cycle Pomodoro complet
                - Citer les cinq étapes de la méthode GTD dans l'ordre

                Conseil : relisez si besoin les leçons "La matrice d'Eisenhower et les lois de la priorisation" et "Étude de cas : réorganiser une semaine surchargée avec la méthode GTD" avant de répondre.

                Bon courage !
                """)
        );
    }

    private List<LessonSpec> devPersoCours3Lessons() {
        return List.of(
            new LessonSpec("Les fondements de la confiance en soi : estime de soi, image de soi et affirmation de soi", Lesson.LessonType.VIDEO, 25, """
                La confiance en soi est une compétence qui se construit et se renforce, elle n'est pas un trait de caractère figé attribué à la naissance. Cette première leçon distingue trois notions souvent confondues.

                TROIS NOTIONS À DISTINGUER
                - L'estime de soi : la valeur globale qu'une personne s'accorde à elle-même, indépendamment de ses résultats ("je vaux quelque chose, même quand j'échoue")
                - La confiance en soi : la croyance en sa capacité à réussir une action précise ("je suis capable de mener ce projet à bien"), qui peut varier d'un domaine à l'autre
                - L'affirmation de soi : la capacité à exprimer ses opinions, ses besoins et ses limites de façon claire et respectueuse, sans agressivité ni soumission

                Une personne peut avoir une bonne confiance en soi dans son domaine professionnel mais manquer d'affirmation de soi dans sa vie personnelle, ou inversement : ce sont des compétences distinctes qui se travaillent séparément.

                D'OÙ VIENT LE MANQUE DE CONFIANCE ?
                - Les expériences d'échec ou de critique répétées, surtout durant l'enfance et l'adolescence
                - La comparaison permanente avec les autres, amplifiée aujourd'hui par les réseaux sociaux
                - Le dialogue intérieur négatif : la voix critique que chacun entretient avec lui-même

                LE DIALOGUE INTÉRIEUR ET LES DISTORSIONS COGNITIVES
                Certaines pensées automatiques faussent notre perception de nous-mêmes :
                - La généralisation excessive : "j'ai raté cette présentation, je suis nul en tout"
                - La pensée dichotomique (tout ou rien) : "si ce n'est pas parfait, c'est un échec total"
                - La personnalisation : s'attribuer la responsabilité d'événements qui ne dépendent pas uniquement de soi

                Apprendre à repérer ces distorsions est la première étape pour reconstruire un dialogue intérieur plus juste et plus bienveillant. Dans les prochaines leçons, nous découvrirons le modèle de l'intelligence émotionnelle, puis des techniques concrètes de communication assertive.
                """),
            new LessonSpec("Le modèle des quatre piliers de l'intelligence émotionnelle (Goleman)", Lesson.LessonType.PDF, 28, """
                L'intelligence émotionnelle (IE) est la capacité à identifier, comprendre et gérer ses propres émotions ainsi que celles des autres. Selon le modèle popularisé par Daniel Goleman, elle repose sur quatre piliers complémentaires.

                PILIER 1 : LA CONSCIENCE DE SOI
                Capacité à reconnaître ses émotions au moment où elles surviennent et à comprendre leur origine.
                - Comportements clés : nommer précisément ce que l'on ressent (colère, frustration, déception, fierté...), identifier les signaux physiques associés (tension dans les épaules, cœur qui s'accélère, mâchoire serrée)
                - Pratique recommandée : tenir un court journal émotionnel quotidien pendant deux semaines

                PILIER 2 : LA MAÎTRISE DE SOI
                Capacité à réguler ses réactions émotionnelles plutôt que de les subir ou de les exprimer de façon impulsive.
                - Comportements clés : marquer une pause avant de réagir sous le coup de l'émotion, utiliser la respiration pour redescendre en tension, choisir consciemment sa réponse plutôt que de réagir automatiquement
                - Pratique recommandée : la technique du "STOP" (S'arrêter, Take a breath/respirer, Observer ce que l'on ressent, Poursuivre en ayant choisi sa réaction)

                PILIER 3 : LA CONSCIENCE SOCIALE (EMPATHIE)
                Capacité à percevoir et comprendre les émotions des autres.
                - Comportements clés : observer le langage corporel et le ton de voix, écouter activement sans préparer sa réponse pendant que l'autre parle, poser des questions pour vérifier sa compréhension des émotions perçues

                PILIER 4 : LA GESTION DES RELATIONS
                Capacité à utiliser sa conscience émotionnelle pour construire des relations saines et gérer les désaccords.
                - Comportements clés : communiquer ses besoins de façon assertive, donner un retour constructif, désamorcer un conflit naissant en reconnaissant l'émotion de l'autre avant de discuter du fond

                POURQUOI CES QUATRE PILIERS SONT LIÉS
                La conscience de soi est le point de départ indispensable : on ne peut pas maîtriser une émotion qu'on n'a pas identifiée, et on comprend mieux les émotions des autres quand on a appris à reconnaître les siennes. Ces quatre piliers se renforcent mutuellement avec la pratique régulière.

                La prochaine leçon présente un outil concret pour s'appuyer sur ces piliers dans les situations de désaccord : la méthode DESC de communication assertive.
                """),
            new LessonSpec("La communication assertive : la méthode DESC pour s'affirmer sans agressivité", Lesson.LessonType.VIDEO, 30, """
                Face à un désaccord ou une situation inconfortable, on observe généralement quatre styles de communication possibles.

                LES QUATRE STYLES DE COMMUNICATION
                - Le style passif : la personne n'exprime pas ses besoins par peur du conflit, au risque de l'accumulation de frustration
                - Le style agressif : la personne impose son point de vue en attaquant ou en dévalorisant l'autre
                - Le style manipulateur : la personne obtient ce qu'elle veut par des détours, la culpabilisation ou le sous-entendu
                - Le style assertif : la personne exprime clairement ses besoins et ses opinions tout en respectant ceux de l'autre

                L'assertivité est l'objectif à viser : elle permet de défendre ses intérêts sans écraser l'autre, et de préserver la relation tout en étant honnête.

                LA MÉTHODE DESC
                Cette méthode structurée en quatre étapes permet de préparer et formuler un message assertif, en particulier dans une situation délicate :
                - D comme Décrire : décrire les faits de façon objective et factuelle, sans jugement ni interprétation
                - E comme Exprimer : exprimer ce que l'on ressent face à ces faits, à la première personne ("je")
                - S comme Spécifier : proposer clairement la solution ou le changement souhaité
                - C comme Conséquences : indiquer les conséquences positives attendues si la solution est adoptée (et éventuellement les conséquences si rien ne change)

                EXEMPLE DE DIALOGUE APPLIQUANT LA MÉTHODE DESC
                Situation : un collègue interrompt systématiquement lors des réunions d'équipe.
                - Décrire : "Lors de nos trois dernières réunions, j'ai remarqué que tu me coupais la parole avant que j'aie terminé mes phrases."
                - Exprimer : "Cela me met mal à l'aise et j'ai l'impression que mes idées ne sont pas prises en compte."
                - Spécifier : "J'aimerais que l'on puisse chacun terminer son idée avant que l'autre ne réponde."
                - Conséquences : "Je pense que cela rendrait nos échanges plus efficaces et plus agréables pour toute l'équipe."

                À ÉVITER LORS DE L'APPLICATION DE LA MÉTHODE
                - Commencer par "tu" (accusateur) plutôt que par "je" (responsable de son propre ressenti)
                - Mélanger plusieurs sujets de désaccord dans un seul message DESC
                - Formuler la demande de façon vague plutôt que concrète et actionnable

                La prochaine leçon applique cette méthode, combinée aux quatre piliers de l'intelligence émotionnelle, à une étude de cas de conflit professionnel complet.
                """),
            new LessonSpec("Étude de cas : gérer un conflit professionnel grâce à l'intelligence émotionnelle", Lesson.LessonType.PDF, 35, """
                ÉTUDE DE CAS : LE CONFLIT ENTRE FATOU ET SON SUPÉRIEUR HIÉRARCHIQUE

                Fatou, responsable administrative, a été critiquée publiquement par son supérieur lors d'une réunion d'équipe pour une erreur dans un rapport. Elle ressent une forte colère mêlée d'humiliation et hésite entre se taire (par crainte des conséquences) et répondre sur le même ton (par réaction impulsive).

                APPLICATION DU PILIER 1 : CONSCIENCE DE SOI
                Avant toute réaction, Fatou prend le temps d'identifier précisément ce qu'elle ressent : de la colère (le fait d'être critiquée devant l'équipe), de l'humiliation (l'impression d'être rabaissée publiquement) et de l'inquiétude (la peur que cela affecte sa réputation professionnelle). Nommer ces émotions précisément l'aide déjà à en réduire l'intensité.

                APPLICATION DU PILIER 2 : MAÎTRISE DE SOI
                Plutôt que de répondre immédiatement sous le coup de l'émotion, Fatou applique la technique du STOP : elle s'arrête, respire profondément, observe ses sensations physiques (mâchoire serrée, respiration courte), et décide consciemment de ne pas réagir en pleine réunion mais de demander un entretien individuel avec son supérieur le lendemain.

                APPLICATION DU PILIER 3 : CONSCIENCE SOCIALE
                Lors de la préparation de l'entretien, Fatou essaie de se mettre à la place de son supérieur : celui-ci était probablement lui-même sous pression suite à une remarque de la direction générale concernant ce même rapport. Cela ne justifie pas la façon dont il s'est exprimé, mais aide Fatou à aborder l'entretien sans hostilité excessive.

                APPLICATION DU PILIER 4 ET DE LA MÉTHODE DESC
                Lors de l'entretien, Fatou structure son message :
                - Décrire : "Lors de la réunion de mardi, tu as commenté mon erreur sur le rapport devant toute l'équipe."
                - Exprimer : "Je me suis sentie humiliée et cela a affecté ma motivation pour le reste de la journée."
                - Spécifier : "À l'avenir, j'aimerais que les remarques sur mon travail me soient d'abord adressées en privé."
                - Conséquences : "Je pense que cela me permettra de mieux recevoir tes retours et de continuer à progresser sereinement."

                RÉSULTAT
                Le supérieur reconnaît sa maladresse et accepte la demande de Fatou. La relation professionnelle, plutôt que de se dégrader, se renforce grâce à un échange assertif et respectueux mené une fois les émotions redescendues.

                TRAVAIL À FAIRE : identifiez une situation de tension que vous avez vécue récemment, et rédigez, en appliquant les quatre piliers et la méthode DESC, le message assertif que vous auriez pu formuler.
                """),
            new LessonSpec("Grille d'auto-évaluation de la confiance en soi et de l'intelligence émotionnelle (exercice Excel)", Lesson.LessonType.EXCEL_EXERCISE, 22, """
                Cet exercice vous propose d'évaluer objectivement votre niveau actuel de confiance en soi et d'intelligence émotionnelle à l'aide d'une grille chiffrée.

                Le tableur contient huit affirmations réparties en deux catégories (Confiance en soi et Intelligence émotionnelle), chacune notée de 0 à 10 selon votre ressenti actuel (0 = pas du tout d'accord, 10 = tout à fait d'accord). Un score global sur 80 est calculé automatiquement, ainsi qu'un pourcentage.

                Votre mission :
                1. Ajustez chaque note de la colonne B selon votre auto-évaluation sincère pour les huit critères proposés
                2. Vérifiez le score global en B14 (formule =SOMME(B4:B12), qui totalise les huit notes en ignorant les lignes de titre)
                3. Observez le pourcentage global en B15 (=B14/80) : un score supérieur à 70 % indique une bonne assise générale, un score entre 40 % et 70 % indique des marges de progression normales, un score inférieur à 40 % suggère de prioriser ce travail personnel
                4. Comparez votre sous-total "Confiance en soi" (lignes 4 à 7) et votre sous-total "Intelligence émotionnelle" (lignes 9 à 12) pour identifier lequel des deux axes mérite le plus d'attention
                5. Choisissez le critère avec la note la plus basse et notez une action concrète pour le renforcer dans les 30 prochains jours (par exemple : pratiquer la méthode DESC lors du prochain désaccord, ou tenir un journal émotionnel quotidien)

                Une fois votre grille complétée et votre plan d'action noté, cliquez sur "Marquer comme terminé".
                """, """
                {"cells":{
                  "A1":{"raw":"GRILLE D'AUTO-ÉVALUATION - CONFIANCE EN SOI & INTELLIGENCE ÉMOTIONNELLE","bold":true},
                  "A3":{"raw":"CONFIANCE EN SOI","bold":true},
                  "B3":{"raw":"Note (/10)","bold":true},
                  "A4":{"raw":"Image de soi (je reconnais mes qualités et compétences)"},
                  "B4":{"raw":"6","format":"number"},
                  "A5":{"raw":"Affirmation de soi (j'exprime mes besoins et opinions)"},
                  "B5":{"raw":"5","format":"number"},
                  "A6":{"raw":"Acceptation de l'échec et résilience"},
                  "B6":{"raw":"7","format":"number"},
                  "A7":{"raw":"Prise de décision et autonomie"},
                  "B7":{"raw":"6","format":"number"},
                  "A8":{"raw":"INTELLIGENCE ÉMOTIONNELLE","bold":true},
                  "A9":{"raw":"Conscience de soi (j'identifie mes émotions)"},
                  "B9":{"raw":"7","format":"number"},
                  "A10":{"raw":"Maîtrise de soi (je régule mes réactions)"},
                  "B10":{"raw":"5","format":"number"},
                  "A11":{"raw":"Empathie (je comprends les émotions des autres)"},
                  "B11":{"raw":"8","format":"number"},
                  "A12":{"raw":"Gestion des relations (je communique, je gère les conflits)"},
                  "B12":{"raw":"6","format":"number"},
                  "A14":{"raw":"SCORE GLOBAL (/80)","bold":true},
                  "B14":{"raw":"=SOMME(B4:B12)","bold":true,"format":"number"},
                  "A15":{"raw":"SCORE GLOBAL (%)","bold":true},
                  "B15":{"raw":"=B14/80","bold":true,"format":"percent"}
                }}
                """),
            new LessonSpec("Quiz : Confiance en soi et intelligence émotionnelle", Lesson.LessonType.QUIZ, 15, """
                Ce quiz récapitule les notions essentielles du module "Confiance en Soi et Intelligence Émotionnelle" :

                - La distinction entre estime de soi, confiance en soi et affirmation de soi
                - Les distorsions cognitives les plus courantes dans le dialogue intérieur (généralisation excessive, pensée dichotomique, personnalisation)
                - Les quatre piliers de l'intelligence émotionnelle selon Goleman : conscience de soi, maîtrise de soi, conscience sociale et gestion des relations
                - Les quatre styles de communication (passif, agressif, manipulateur, assertif) et la méthode DESC pour s'affirmer avec respect
                - L'application combinée de ces outils à un cas concret de résolution de conflit professionnel

                Avant de répondre, vérifiez que vous savez :
                - Nommer les quatre lettres de la méthode DESC et ce que chacune signifie
                - Distinguer un message assertif d'un message agressif ou passif
                - Associer chaque pilier de l'intelligence émotionnelle à un comportement concret

                Conseil : relisez si besoin les leçons "Le modèle des quatre piliers de l'intelligence émotionnelle (Goleman)", "La communication assertive : la méthode DESC pour s'affirmer sans agressivité" et l'étude de cas de Fatou avant de vous tester.

                Bon courage !
                """)
        );
    }
}
