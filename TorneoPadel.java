import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CAMPLUS PADEL CUP 2026 - Generatore Accoppiamenti Ottavi
 *
 * Input:  classifiche finali gironi + risultati spareggi
 * Output: classifiche generali, spareggi, ottavi GOLD/SILVER con albero
 *
 * Criteri spareggio nel girone (in ordine):
 *   1. Punti vittoria (già nel file)
 *   2. Scontro diretto (interattivo al primo lancio, poi salvato nel file)
 *   3. Differenza set
 *   4. Differenza game
 *   5. Monetina (interattivo al primo lancio, poi salvato nel file)
 *
 * Le risposte ai tie-break vengono scritte nella sezione TIEBREAK del file
 * di input, così ai lanci successivi non viene chiesto nulla.
 *
 * Uso: java TorneoPadel <file.txt>
 */
public class TorneoPadel {

    private static final int NUM_GIRONI = 8;
    private static final int NUM_SPAREGGI = 8;

    // ──────────────────────────────────────────────────────────────
    //  Domain model
    // ──────────────────────────────────────────────────────────────

    static class Squadra {
        final String nome;
        final int girone;
        final int vittorie;
        final int diffSet;
        final int diffGame;

        Squadra(String nome, int girone, int vittorie, int diffSet, int diffGame) {
            this.nome = nome;
            this.girone = girone;
            this.vittorie = vittorie;
            this.diffSet = diffSet;
            this.diffGame = diffGame;
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    record Partita(Squadra squadraA, Squadra squadraB) {}

    // ──────────────────────────────────────────────────────────────
    //  Parsed tournament data
    // ──────────────────────────────────────────────────────────────

    static class DatiTorneo {
        final List<List<Squadra>> gironi = new ArrayList<>();
        int[] risultatiSpareggi;

        DatiTorneo() {
            for (int i = 0; i < NUM_GIRONI; i++) {
                gironi.add(new ArrayList<>());
            }
        }

        List<Squadra> girone(int indice) {
            return gironi.get(indice);
        }

        void setGirone(int indice, List<Squadra> squadre) {
            gironi.set(indice, squadre);
        }

        boolean gironiCompleti() {
            return gironi.stream().allMatch(g -> g.size() >= 4);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Tie-break persistence
    // ──────────────────────────────────────────────────────────────

    static class TieBreakStore {
        private final Map<String, Integer> salvati = new LinkedHashMap<>();
        private final Map<String, Integer> nuovi = new LinkedHashMap<>();

        void caricaDaFile(String chiave, int valore) {
            salvati.put(chiave, valore);
        }

        boolean contiene(String chiave) {
            return salvati.containsKey(chiave);
        }

        int get(String chiave) {
            return salvati.get(chiave);
        }

        void aggiungi(String chiave, int valore) {
            nuovi.put(chiave, valore);
        }

        boolean haNuoviDati() {
            return !nuovi.isEmpty();
        }

        Map<String, Integer> tutti() {
            Map<String, Integer> unione = new LinkedHashMap<>(salvati);
            unione.putAll(nuovi);
            return unione;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Console I/O helper
    // ──────────────────────────────────────────────────────────────

    static class ConsoleIO {
        private final Scanner scanner;

        ConsoleIO() {
            this.scanner = new Scanner(System.in, "UTF-8");
        }

        int leggiIntero(int min, int max) {
            while (true) {
                try {
                    int val = Integer.parseInt(scanner.nextLine().trim());
                    if (val >= min && val <= max) return val;
                } catch (NumberFormatException ignored) {}
                System.out.printf("  │  Inserisci un numero tra %d e %d: ", min, max);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  File parser
    // ──────────────────────────────────────────────────────────────

    static class FileParser {

        static DatiTorneo parse(String path, TieBreakStore store) throws IOException {
            List<String> righe = leggiRighe(path);
            DatiTorneo dati = new DatiTorneo();
            int gironeCorrente = -1;
            String sezione = "";

            for (String riga : righe) {
                if (riga.matches("GIRONE \\d+")) {
                    gironeCorrente = Integer.parseInt(riga.split(" ")[1]) - 1;
                    sezione = "GIRONE";
                    continue;
                }
                if (riga.equals("SPAREGGI")) { sezione = "SPAREGGI"; continue; }
                if (riga.equals("TIEBREAK")) { sezione = "TIEBREAK"; continue; }

                switch (sezione) {
                    case "GIRONE"   -> parseRigaGirone(riga, gironeCorrente, dati);
                    case "SPAREGGI" -> parseRigaSpareggi(riga, dati);
                    case "TIEBREAK" -> parseRigaTieBreak(riga, store);
                }
            }
            return dati;
        }

        private static List<String> leggiRighe(String path) throws IOException {
            List<String> righe = new ArrayList<>();
            try (var br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
                String riga;
                while ((riga = br.readLine()) != null) {
                    String trimmed = riga.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        righe.add(trimmed);
                    }
                }
            }
            return righe;
        }

        private static void parseRigaGirone(String riga, int gironeIdx, DatiTorneo dati) {
            String[] campi = riga.split("\\s*;\\s*");
            if (campi.length != 4 || campi[0].contains("<")) return;

            dati.girone(gironeIdx).add(new Squadra(
                    campi[0].trim(),
                    gironeIdx + 1,
                    Integer.parseInt(campi[1].trim()),
                    Integer.parseInt(campi[2].trim()),
                    Integer.parseInt(campi[3].trim())));
        }

        private static void parseRigaSpareggi(String riga, DatiTorneo dati) {
            String cleaned = riga.replaceAll("[;<>]", "").trim();
            if (cleaned.isEmpty() || cleaned.contains("o")) return;

            String[] tokens = cleaned.split("\\s+");
            if (tokens.length != NUM_SPAREGGI) return;

            try {
                dati.risultatiSpareggi = new int[NUM_SPAREGGI];
                for (int i = 0; i < NUM_SPAREGGI; i++) {
                    dati.risultatiSpareggi[i] = Integer.parseInt(tokens[i]);
                }
            } catch (NumberFormatException e) {
                dati.risultatiSpareggi = null;
            }
        }

        private static void parseRigaTieBreak(String riga, TieBreakStore store) {
            String[] campi = riga.split("\\s*;\\s*");
            if (campi.length != 2) return;

            try {
                store.caricaDaFile(campi[0].trim(), Integer.parseInt(campi[1].trim()));
            } catch (NumberFormatException ignored) {}
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  File writer (tie-break persistence)
    // ──────────────────────────────────────────────────────────────

    static class FileWriter {

        static void salvaTieBreak(String filePath, TieBreakStore store) throws IOException {
            List<String> righeOriginali = leggiRigheSenzaTieBreak(filePath);

            try (var pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
                righeOriginali.forEach(pw::println);
                pw.println();
                pw.println("TIEBREAK");
                store.tutti().forEach((chiave, valore) ->
                        pw.println(chiave + " ; " + valore));
            }
        }

        private static List<String> leggiRigheSenzaTieBreak(String path) throws IOException {
            List<String> righe = new ArrayList<>();
            try (var br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
                String riga;
                boolean inTiebreak = false;
                while ((riga = br.readLine()) != null) {
                    if (riga.trim().equals("TIEBREAK")) { inTiebreak = true; continue; }
                    if (!inTiebreak) righe.add(riga);
                }
            }
            return righe;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Tie-break resolver
    // ──────────────────────────────────────────────────────────────

    static class TieBreakResolver {
        private final TieBreakStore store;
        private final ConsoleIO console;

        TieBreakResolver(TieBreakStore store, ConsoleIO console) {
            this.store = store;
            this.console = console;
        }

        List<Squadra> ordinaGirone(List<Squadra> squadre, int numGirone) {
            List<Squadra> lista = new ArrayList<>(squadre);
            lista.sort(Comparator.comparingInt((Squadra s) -> s.vittorie).reversed());
            return risolviParitaVittorie(lista, numGirone);
        }

        private List<Squadra> risolviParitaVittorie(List<Squadra> lista, int numGirone) {
            List<Squadra> risultato = new ArrayList<>();
            int i = 0;

            while (i < lista.size()) {
                int j = i + 1;
                while (j < lista.size() && lista.get(j).vittorie == lista.get(i).vittorie) j++;

                if (j - i == 1) {
                    risultato.add(lista.get(i));
                } else {
                    List<Squadra> blocco = new ArrayList<>(lista.subList(i, j));
                    risultato.addAll(risolviBlocco(blocco, numGirone));
                }
                i = j;
            }
            return risultato;
        }

        private List<Squadra> risolviBlocco(List<Squadra> blocco, int numGirone) {
            stampaIntestazioneParita(blocco, numGirone);
            List<Squadra> ordinati = applicaScontriDiretti(blocco, numGirone);
            System.out.println("  └─────────────────────────────────────────────────────────────────");
            return ordinati;
        }

        private void stampaIntestazioneParita(List<Squadra> blocco, int numGirone) {
            System.out.println();
            System.out.println("  ┌─ SPAREGGIO CLASSIFICA - GIRONE " + numGirone
                    + " ─────────────────────────────");
            System.out.println("  │  Le seguenti squadre hanno lo stesso numero di punti vittoria:");
            for (int k = 0; k < blocco.size(); k++) {
                System.out.printf("    %d) %s%n", k + 1, blocco.get(k).nome);
            }
        }

        // --- Criterio 2: scontri diretti ---

        private List<Squadra> applicaScontriDiretti(List<Squadra> blocco, int numGirone) {
            int n = blocco.size();
            int[] puntiSD = calcolaPuntiScontriDiretti(blocco, numGirone);

            // Ordina per punti scontri diretti decrescenti
            Integer[] indici = new Integer[n];
            for (int i = 0; i < n; i++) indici[i] = i;
            Arrays.sort(indici, (a, b) -> puntiSD[b] - puntiSD[a]);

            List<Squadra> ordinati = Arrays.stream(indici)
                    .map(blocco::get)
                    .collect(Collectors.toList());
            int[] puntiOrdinati = Arrays.stream(indici)
                    .mapToInt(idx -> puntiSD[idx])
                    .toArray();

            return risolviSottoBlocchi(ordinati, puntiOrdinati, numGirone);
        }

        private int[] calcolaPuntiScontriDiretti(List<Squadra> blocco, int numGirone) {
            int n = blocco.size();
            boolean[][] wins = new boolean[n][n];

            System.out.println("  │");
            System.out.println("  │  [Criterio 2: SCONTRI DIRETTI]");

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    int vincitore = chiediScontroDiretto(blocco.get(i), blocco.get(j), numGirone);
                    wins[i][j] = (vincitore == 1);
                    wins[j][i] = (vincitore == 2);
                }
            }

            int[] punti = new int[n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i != j && wins[i][j]) punti[i]++;
                }
            }
            return punti;
        }

        private int chiediScontroDiretto(Squadra a, Squadra b, int numGirone) {
            String chiave = chiaveScontroDiretto(numGirone, a.nome, b.nome);

            if (store.contiene(chiave)) {
                int risp = store.get(chiave);
                System.out.printf("  │  %s  vs  %s  →  %s  [salvato]%n",
                        a.nome, b.nome, risp == 1 ? a.nome : b.nome);
                return risp;
            }

            System.out.printf("  │  Chi ha vinto lo scontro diretto tra:%n");
            System.out.printf("  │    1) %s%n", a.nome);
            System.out.printf("  │    2) %s%n", b.nome);
            System.out.print("  │  Inserisci 1 o 2: ");
            int risp = console.leggiIntero(1, 2);
            store.aggiungi(chiave, risp);
            return risp;
        }

        private List<Squadra> risolviSottoBlocchi(
                List<Squadra> ordinati, int[] punti, int numGirone) {
            List<Squadra> risultato = new ArrayList<>();
            int i = 0;

            while (i < ordinati.size()) {
                int j = i + 1;
                while (j < ordinati.size() && punti[j] == punti[i]) j++;

                List<Squadra> sotto = new ArrayList<>(ordinati.subList(i, j));
                if (sotto.size() > 1) {
                    sotto = applicaCriteriSecondari(sotto, numGirone);
                }
                risultato.addAll(sotto);
                i = j;
            }
            return risultato;
        }

        // --- Criteri 3 e 4: differenza set e game ---

        private List<Squadra> applicaCriteriSecondari(List<Squadra> blocco, int numGirone) {
            if (blocco.size() <= 1) return blocco;

            blocco.sort(Comparator.comparingInt((Squadra s) -> s.diffSet).reversed()
                    .thenComparing(Comparator.comparingInt((Squadra s) -> s.diffGame).reversed()));

            List<Squadra> risultato = new ArrayList<>();
            int i = 0;

            while (i < blocco.size()) {
                int j = i + 1;
                while (j < blocco.size()
                        && blocco.get(j).diffSet == blocco.get(i).diffSet
                        && blocco.get(j).diffGame == blocco.get(i).diffGame) {
                    j++;
                }

                List<Squadra> sotto = new ArrayList<>(blocco.subList(i, j));
                if (sotto.size() > 1) {
                    stampaParitaSetGame(sotto);
                    sotto = applicaMonetina(sotto, numGirone, 1);
                }
                risultato.addAll(sotto);
                i = j;
            }
            return risultato;
        }

        private void stampaParitaSetGame(List<Squadra> sotto) {
            System.out.println("  │");
            System.out.println("  │  [Criterio 3+4: diff. set e diff. game identici per:]");
            for (Squadra s : sotto) {
                System.out.printf("  │    - %s  (dSet=%+d, dGame=%+d)%n",
                        s.nome, s.diffSet, s.diffGame);
            }
        }

        // --- Criterio 5: monetina ---

        private List<Squadra> applicaMonetina(List<Squadra> blocco, int numGirone, int posizione) {
            if (blocco.size() <= 1) return blocco;

            String chiave = chiaveMonetina(numGirone, posizione, blocco);
            int vincitoreIdx = chiediMonetina(chiave, blocco);

            // Sposta il vincitore in testa
            List<Squadra> risultato = new ArrayList<>();
            risultato.add(blocco.get(vincitoreIdx));
            for (int i = 0; i < blocco.size(); i++) {
                if (i != vincitoreIdx) risultato.add(blocco.get(i));
            }

            // Risolvi ricorsivamente le posizioni rimanenti
            if (risultato.size() > 2) {
                List<Squadra> restanti = applicaMonetina(
                        new ArrayList<>(risultato.subList(1, risultato.size())),
                        numGirone, posizione + 1);
                risultato = new ArrayList<>();
                risultato.add(blocco.get(vincitoreIdx));
                risultato.addAll(restanti);
            }

            return risultato;
        }

        private int chiediMonetina(String chiave, List<Squadra> blocco) {
            if (store.contiene(chiave)) {
                int risp = store.get(chiave);
                System.out.printf("  │  [Monetina] Vincitore: %s  [salvato]%n",
                        blocco.get(risp - 1).nome);
                return risp - 1;
            }

            System.out.println("  │");
            System.out.println("  │  [Criterio 5: LANCIO DELLA MONETINA]");
            System.out.println("  │  Parità totale. Chi ha vinto il lancio della monetina?");
            for (int k = 0; k < blocco.size(); k++) {
                System.out.printf("  │    %d) %s%n", k + 1, blocco.get(k).nome);
            }
            System.out.print("  │  Inserisci il numero della squadra vincitrice: ");
            int risp = console.leggiIntero(1, blocco.size());
            store.aggiungi(chiave, risp);
            return risp - 1;
        }

        // --- Key builders ---

        private String chiaveScontroDiretto(int girone, String nomeA, String nomeB) {
            return "G" + girone + "_SD_"
                    + normalizzaNome(nomeA) + "_vs_" + normalizzaNome(nomeB);
        }

        private String chiaveMonetina(int girone, int posizione, List<Squadra> blocco) {
            return "G" + girone + "_MON_" + posizione + "_"
                    + normalizzaNome(blocco.get(0).nome) + "_"
                    + normalizzaNome(blocco.get(blocco.size() - 1).nome);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Rankings
    // ──────────────────────────────────────────────────────────────

    static class Classifiche {

        static List<Squadra> generalePerPosizione(DatiTorneo dati, int posizione) {
            return dati.gironi.stream()
                    .filter(g -> posizione < g.size())
                    .map(g -> g.get(posizione))
                    .sorted(Comparator.comparingInt((Squadra s) -> s.vittorie).reversed()
                            .thenComparing(Comparator.comparingInt((Squadra s) -> s.diffSet).reversed())
                            .thenComparing(Comparator.comparingInt((Squadra s) -> s.diffGame).reversed()))
                    .collect(Collectors.toList());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Console output / formatting
    // ──────────────────────────────────────────────────────────────

    static class Printer {

        static void box(String text) {
            int len = text.length() + 4;
            System.out.println("╔" + "═".repeat(len) + "╗");
            System.out.println("║  " + text + "  ║");
            System.out.println("╚" + "═".repeat(len) + "╝");
        }

        static void classificaGirone(List<Squadra> squadre, int numGirone) {
            System.out.println("== GIRONE " + numGirone + " " + "=".repeat(55));
            System.out.printf("  %-3s %-30s %4s %5s %6s%n", "Pos", "Squadra", "V", "dSet", "dGame");
            for (int i = 0; i < squadre.size(); i++) {
                Squadra s = squadre.get(i);
                System.out.printf("  %d.  %-30s %4d %+5d %+6d%n",
                        i + 1, s.nome, s.vittorie, s.diffSet, s.diffGame);
            }
            System.out.println();
        }

        static void classificaGenerale(String titolo, List<Squadra> lista) {
            System.out.println("\n  " + titolo);
            System.out.printf("  %-4s %-30s %4s %5s %6s  %s%n",
                    "Rank", "Squadra", "V", "dSet", "dGame", "Girone");
            System.out.println("  " + "-".repeat(75));
            for (int i = 0; i < lista.size(); i++) {
                Squadra s = lista.get(i);
                System.out.printf("  %d.   %-30s %4d %+5d %+6d  G%d%n",
                        i + 1, s.nome, s.vittorie, s.diffSet, s.diffGame, s.girone);
            }
        }

        static void accoppiamentiSpareggi(Squadra[] seconde, Squadra[] terze) {
            System.out.println("\n  Accoppiamenti:");
            for (int i = 0; i < NUM_SPAREGGI; i++) {
                System.out.printf("    Sp.%d:  %-30s (%da sec.)   vs   %-30s (%da terza)%n",
                        i + 1, seconde[i].nome, i + 1, terze[i].nome, NUM_SPAREGGI - i);
            }
        }

        static void risultatiSpareggi(Squadra[] seconde, Squadra[] terze, Squadra[] vincenti) {
            System.out.println("\n  Risultati:");
            for (int i = 0; i < NUM_SPAREGGI; i++) {
                System.out.printf("    Sp.%d:  %-28s vs %-28s  =>  %s%n",
                        i + 1, seconde[i].nome, terze[i].nome, vincenti[i].nome);
            }
        }

        static void ottaviLista(Partita[] ottavi) {
            System.out.printf("  %-7s %-30s     %-30s%n", "Match", "Squadra A", "Squadra B");
            System.out.println("  " + "-".repeat(75));
            for (int i = 0; i < ottavi.length; i++) {
                System.out.printf("  Ott.%d   %-30s vs  %-30s%n",
                        i + 1, ottavi[i].squadraA().nome, ottavi[i].squadraB().nome);
            }
        }

        static void alberoOttavi(String nome, Partita[] ottavi) {
            int W = 26;
            String border = "═".repeat(W * 2 + 40);

            System.out.println("  ╔" + border + "╗");
            System.out.println("  ║" + center("TABELLONE " + nome + " - OTTAVI", W * 2 + 40) + "║");
            System.out.println("  ╚" + border + "╝");
            System.out.println();

            System.out.println("  ┌─ SEMIFINALE 1 " + "─".repeat(69));
            System.out.println("  │");
            System.out.println("  │   ┌─ QUARTI 1 (Vinc.Ott1 vs Vinc.Ott8)");
            System.out.println("  │   │");
            stampaMatchBracket(ottavi[0], W, "  │   │   ");
            System.out.println("  │   │");
            stampaMatchBracket(ottavi[7], W, "  │   │   ");
            System.out.println("  │   │");
            System.out.println("  │   │");
            System.out.println("  │   └─ QUARTI 4 (Vinc.Ott4 vs Vinc.Ott5)");
            System.out.println("  │");
            stampaMatchBracket(ottavi[3], W, "  │       ");
            System.out.println("  │");
            stampaMatchBracket(ottavi[4], W, "  │       ");
            System.out.println("  │");
            System.out.println("  │");
            System.out.println("  └─────────── FINALE ─── Vinc. Semi 1  vs  Vinc. Semi 2");
            System.out.println("  ┌───────────");
            System.out.println("  │");
            System.out.println("  └─ SEMIFINALE 2 " + "─".repeat(69));
            System.out.println();
            System.out.println("      ┌─ QUARTI 2 (Vinc.Ott2 vs Vinc.Ott7)");
            System.out.println("      │");
            stampaMatchBracket(ottavi[1], W, "      │   ");
            System.out.println("      │");
            stampaMatchBracket(ottavi[6], W, "      │   ");
            System.out.println("      │");
            System.out.println("      │");
            System.out.println("      └─ QUARTI 3 (Vinc.Ott3 vs Vinc.Ott6)");
            System.out.println();
            stampaMatchBracket(ottavi[2], W, "          ");
            System.out.println();
            stampaMatchBracket(ottavi[5], W, "          ");
            System.out.println();
        }

        private static void stampaMatchBracket(Partita match, int W, String indent) {
            String n1 = trunc(match.squadraA().nome, W);
            String n2 = trunc(match.squadraB().nome, W);
            String g1 = "(G" + match.squadraA().girone + ")";
            String g2 = "(G" + match.squadraB().girone + ")";

            System.out.printf("%s┌─────────────────────────────────────┐%n", indent);
            System.out.printf("%s│  %-" + W + "s %-4s  │%n", indent, n1, g1);
            System.out.printf("%s│          vs                        │%n", indent);
            System.out.printf("%s│  %-" + W + "s %-4s  │%n", indent, n2, g2);
            System.out.printf("%s└─────────────────────────────────────┘%n", indent);
        }

        private static String center(String s, int width) {
            if (s.length() >= width) return s;
            int pad = (width - s.length()) / 2;
            return " ".repeat(pad) + s + " ".repeat(width - s.length() - pad);
        }

        private static String trunc(String s, int max) {
            return s.length() <= max ? s : s.substring(0, max - 2) + "..";
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Utility
    // ──────────────────────────────────────────────────────────────

    static String normalizzaNome(String nome) {
        return nome.replaceAll("[^a-zA-Z0-9]", "_");
    }

    // ──────────────────────────────────────────────────────────────
    //  MAIN
    // ──────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java TorneoPadel <file.txt>");
            System.exit(1);
        }
        String filePath = args[0];

        // 1. Parse input
        TieBreakStore store = new TieBreakStore();
        DatiTorneo dati = FileParser.parse(filePath, store);

        // 2. Sort each group with full tie-breaking
        ConsoleIO console = new ConsoleIO();
        TieBreakResolver resolver = new TieBreakResolver(store, console);

        for (int g = 0; g < NUM_GIRONI; g++) {
            if (!dati.girone(g).isEmpty()) {
                dati.setGirone(g, resolver.ordinaGirone(dati.girone(g), g + 1));
            }
        }

        // 3. Persist new tie-break answers
        if (store.haNuoviDati()) {
            FileWriter.salvaTieBreak(filePath, store);
            System.out.println();
            System.out.println("  [Le risposte ai tie-break sono state salvate nel file.]");
            System.out.println("  [Al prossimo lancio non verranno richieste.]");
            System.out.println();
        }

        // 4. Print group standings
        Printer.box("CAMPLUS PADEL CUP 2026 - TORNEO MASCHILE");
        System.out.println();

        for (int g = 0; g < NUM_GIRONI; g++) {
            if (!dati.girone(g).isEmpty()) {
                Printer.classificaGirone(dati.girone(g), g + 1);
            }
        }

        // 5. General rankings by position (art.8)
        List<Squadra> prime   = Classifiche.generalePerPosizione(dati, 0);
        List<Squadra> seconde = Classifiche.generalePerPosizione(dati, 1);
        List<Squadra> terze   = Classifiche.generalePerPosizione(dati, 2);
        List<Squadra> quarte  = Classifiche.generalePerPosizione(dati, 3);

        Printer.classificaGenerale("CLASSIFICA GENERALE - PRIME",   prime);
        Printer.classificaGenerale("CLASSIFICA GENERALE - SECONDE", seconde);
        Printer.classificaGenerale("CLASSIFICA GENERALE - TERZE",   terze);
        Printer.classificaGenerale("CLASSIFICA GENERALE - QUARTE",  quarte);

        if (!dati.gironiCompleti()) {
            System.out.println("\n  >>> Mancano dati per alcuni gironi. Servono tutti e 8. <<<");
            return;
        }

        // 6. Playoffs 2nd vs 3rd (art.9)
        System.out.println();
        Printer.box("SPAREGGI 2a vs 3a (art.9)");

        Squadra[] sparSeconde = new Squadra[NUM_SPAREGGI];
        Squadra[] sparTerze   = new Squadra[NUM_SPAREGGI];
        for (int i = 0; i < NUM_SPAREGGI; i++) {
            sparSeconde[i] = seconde.get(i);
            sparTerze[i]   = terze.get(NUM_SPAREGGI - 1 - i);
        }

        Printer.accoppiamentiSpareggi(sparSeconde, sparTerze);

        if (dati.risultatiSpareggi == null) {
            System.out.println("\n  >>> Risultati spareggi non inseriti. <<<");
            System.out.println("  >>> Aggiungi la sezione SPAREGGI nel file e riesegui. <<<");
            return;
        }

        // Determine winners and losers
        Squadra[] vincSpar = new Squadra[NUM_SPAREGGI];
        Squadra[] perdSpar = new Squadra[NUM_SPAREGGI];
        for (int i = 0; i < NUM_SPAREGGI; i++) {
            boolean vinceSeconda = (dati.risultatiSpareggi[i] == 1);
            vincSpar[i] = vinceSeconda ? sparSeconde[i] : sparTerze[i];
            perdSpar[i] = vinceSeconda ? sparTerze[i]   : sparSeconde[i];
        }

        Printer.risultatiSpareggi(sparSeconde, sparTerze, vincSpar);

        // 7. Round of 16 - GOLD (art.10)
        Partita[] goldOttavi = new Partita[NUM_SPAREGGI];
        for (int i = 0; i < NUM_SPAREGGI; i++) {
            goldOttavi[i] = new Partita(prime.get(i), vincSpar[NUM_SPAREGGI - 1 - i]);
        }

        // 8. Round of 16 - SILVER (art.11)
        Partita[] silverOttavi = new Partita[NUM_SPAREGGI];
        for (int i = 0; i < NUM_SPAREGGI; i++) {
            silverOttavi[i] = new Partita(quarte.get(i), perdSpar[i]);
        }

        // 9. Print brackets
        System.out.println();
        Printer.box("TORNEO GOLD - OTTAVI DI FINALE (art.10)");
        System.out.println();
        Printer.ottaviLista(goldOttavi);
        System.out.println();
        Printer.alberoOttavi("GOLD", goldOttavi);

        System.out.println();
        Printer.box("TORNEO SILVER - OTTAVI DI FINALE (art.11)");
        System.out.println();
        Printer.ottaviLista(silverOttavi);
        System.out.println();
        Printer.alberoOttavi("SILVER", silverOttavi);
    }
}