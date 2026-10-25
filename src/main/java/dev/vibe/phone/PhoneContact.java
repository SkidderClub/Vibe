package dev.vibe.phone;

/** Scripted, fictional VibeOS personas. Numbers are display-only and never dialled. */
public enum PhoneContact {
    ESOUND("esound", "ESound", "+67 1874/2069", "Augustus developer", "esound.jpg", 0xFF597CA5),
    TRUMP("trump", "Donald J Trump", "911", "Fictional presidential parody", "trump.png", 0xFFC79051,
            new String[][] {
                {"Will you release the Epstein files?", "The files? Tremendous stacks of paper. My office has misplaced the filing cabinet again.", "Transparency! Wonderful word. My assistant says the paperwork is still paperwork. Ask me after golf."},
                {"Why do you always dodge the files question?", "I was answering! Then someone brought up golf. Very distracting. Back to the paperwork, eventually.", "My press secretary has prepared a statement: another statement is being prepared."},
                {"Are aliens hiding at Area 51?", "People tell me things. Many things. I have no alien evidence, but the parking there is apparently terrible.", "If aliens visit, I expect a very large welcome sign. Until then, that is internet speculation."},
                {"Was the moon landing real?", "Yes. But imagine the golf courses. Terrible atmosphere, tremendous distance on the drives.", "The landing happened. My lunar hotel proposal, however, exists entirely in this silly phone."},
                {"Is the Earth secretly flat?", "Round. Like a golf ball. The internet has somehow made this a very long meeting.", "A globe! I have seen one. Excellent shape. Conspiracy meeting adjourned."},
                {"Do pigeons work for the government?", "No evidence of that. One did steal my sandwich. Very suspicious lunch, very ordinary bird.", "That is a joke theory. Although the pigeons in this game have excellent timing."},
                {"Are you hiding a secret golf bunker?", "In this fictional phone? Absolutely. It contains three golf balls and an unfinished press statement.", "You caught me: the secret is a second putting green. Entirely fictional, very exclusive."},
                {"What is your greatest conspiracy?", "The printer only jams when I need a document. That is my entire evidence board.", "Someone keeps moving my reading glasses. They were on my head. Case closed."}
            }, new String[] {
                "Saw your call. My fictional office is reviewing your very important voicemail. Probably after golf.",
                "Missed the call. I was dictating a tremendous statement about why I missed the call."
            }),
    LUCKY("lucky", "Lucky", "+33 6 89 25 93 14", "Polar developer · France", "lucky.png", 0xFF5BAFCE,
            new String[][] {
                {"Did you patch Augustus again?", "Bonjour! ESound finishes a config and I finish a patch. Quelle coincidence.", "Oui. Tell ESound the changelog says bonjour. He knows what that means."},
                {"ESound says his Polar config is unbeatable.", "Ah bon? Tell him I appreciate the optimism. My test server is ready.", "Magnifique. Another unbeatable config for the patch museum."},
                {"Can ESound have one evening without a patch?", "D'accord, he may have dinner. I make no promises about dessert.", "Mais non, I am not cruel. I wait until he has carried the groceries inside."},
                {"Is Polar better than Intave?", "You want to start a developer argument? Tres bien. Ask Richy and bring popcorn.", "I am obviously voting for myself. Quelle surprise!"},
                {"How is the next Polar update going?", "Cafe, tests, repeat. ESound will receive the dramatic version later.", "Ca va bien. I have named the next patch 'Good evening, ESound'."},
                {"Richy called Intave the golden standard.", "Bonjour to Richy. Gold is lovely; I prefer a changelog that ruins ESound's afternoon.", "Respect for the history. Now please stop forwarding our group chat arguments."},
                {"Please stop patching my favorite config.", "Desole! You have reached the wrong developer for that request.", "Mon ami, this is Polar support, not a config preservation society."}
            }, new String[] {
                "Bonjour! Missed your call. I was testing another Polar patch. ESound already guessed.",
                "Desole, I was away from the phone. Send the test case, not twenty missed calls."
            }),
    RICHY("richy", "Richy", "+49 151 8010 0560", "Intave developer · Germany", "richy.png", 0xFFB67FBB,
            new String[][] {
                {"Was Intave the golden standard?", "War? WAR? Verdammte Axt, ich habe gerade erst den Kaffee aufgemacht.", "Goldener Standard steht noch auf meiner Tasse. Die bleibt hier."},
                {"Lucky says Polar is way better now.", "Verdammte Axt. Hat Lucky dir wenigstens ein Diagramm zu diesem Ragebait mitgeschickt?", "Dann soll Lucky meine Support-Warteschlange gleich mit uebernehmen."},
                {"Is your debugger older than Polar?", "hdf du hs. Mein Debugger hat mehr Berufserfahrung als diese Frage.", "Ja klar, und mein Kaffee laeuft auf Windows 98. Sehr originell."},
                {"Did Lucky patch Augustus before you again?", "Ich messe meine Arbeitszeit nicht in ESound-Traenen. Meistens jedenfalls.", "Ich habe auch andere Hobbys. Zum Beispiel ueber solche Fragen fluchen."},
                {"When is the next Intave update?", "Wenn es fertig ist. Verdammte Axt, die Frage hat sogar meine IDE genervt.", "Nach dem Kaffee. Vor dem naechsten Kaffee. Irgendwo dazwischen."},
                {"Should I ask Polar support instead?", "Mach doch. Sag Lucky, er schuldet mir noch eine ruhige Mittagspause.", "Klar. Schick ihm diese Frage gleich dreimal, damit er das volle Erlebnis bekommt."},
                {"Would you put a gold medal on Intave?", "Endlich eine vernuenftige Frage. Mindestens zwei, verdammte Axt.", "Nur wenn Lucky sie nicht vorher mit einem Changelog ueberklebt."}
            }, new String[] {
                "Verdammte Axt, verpasst. Schreib mir. Und bitte keine neue Polar-Vergleichstabelle.",
                "War im Debugger. Wenn das wieder Ragebait ist, antworte wenigstens mit einem guten Witz."
            }),
    BIPAS("bipas", "Bipas", "+221 76 836 87 89", "Vibe developer · Senegal", "bipas.png", 0xFF68BBA5,
            new String[][] {
                {"I love Vibe!", "Thank you! That means a lot. I'm happy you're enjoying Vibe.", "Much appreciated! Messages like this make building Vibe even more fun."},
                {"Vibe is my favorite client.", "Thank you for the support! I'm glad Vibe has earned a place in your setup.", "That makes my day. Thanks for choosing Vibe!"},
                {"Thank you for building Vibe!", "You're very welcome! Thanks for being part of it.", "Thank you for the kind words. I'm happy you like what I'm building."},
                {"The new Vibe features look amazing.", "Thanks! I put a lot of care into them. Enjoy the update!", "I appreciate it! It's lovely to hear the details are making a difference."},
                {"Your work on Vibe makes me happy.", "That's such a nice message. Thank you, and have a great game!", "Thank you! Hearing that is a real motivation to keep improving Vibe."},
                {"Keep up the great work on Vibe!", "I will! Thank you for cheering me on.", "Thanks for the encouragement! Sending good vibes right back."}
            }, new String[] {
                "Thanks for calling! I missed it while working on Vibe. Your support means a lot.",
                "Hey, sorry I missed your call. Thanks for all the love for Vibe!"
            }),
    VOGEL("vogel", "Vogel", "+91 85197 39635", "Augustus keys · India", "vogel.png", 0xFFC18B63,
            new String[][] {{"I want augustus key"}}, new String[] {
                "Missed your call. If this is about an Augustus key, the conversation might be very short.",
                "Can't talk right now. The key requests are already filling the screen."
            }),
    CARL("carl", "Carl", "+49 157 8180 0919", "Germany", null, 0xFF7F93B8,
            new String[][] {
                {"Hey Carl, what's new?", "Moin! Kurze Frage: Interesse an Lachgas? Ach so, du wolltest nur reden.", "Servus! Ich habe einen Verkaufsspruch vorbereitet. Er enthaelt das Wort Lachgas."},
                {"What about those scam client rumors?", "Also erstmal: anderes Thema. Wie stehst du zu meinem Lachgas-Werbejingle?", "Dazu aeussere ich mich nicht. Aber ich habe einen absolut unnoetigen Lachgas-Flyer entworfen."},
                {"Can you answer a question about your clients?", "Klar, eine Frage. Moment, meinst du Clients oder Kunden? Jedenfalls: Lachgas!", "Die technische Abteilung ist gerade nicht da. Die Abteilung Lachgas ist leider immer da."},
                {"Can we talk about something besides Lachgas?", "Natuerlich. Wie war dein Tag? Meiner war... ach Mist, jetzt wollte ich wieder Lachgas sagen.", "Ja. Ich zaehle bis drei. Eins, zwei, Lachgas. Das lief nicht nach Plan."},
                {"Why are you always trying to sell Lachgas?", "Ich brauche dringend ein neues Gespraechsthema. Du kennst nicht zufaellig eins mit Lachgas?", "Mein Marketingplan besteht aus einem Wort. Du kannst es vermutlich erraten."},
                {"Are you avoiding my client question?", "Ich? Ausweichen? Niemals. Hast du schon meinen neuen Lachgas-Slogan gesehen?", "Nein nein, ich leite nur sehr elegant zur imaginaren Lachgas-Abteilung weiter."},
                {"Do you ever take a day off from selling?", "Sonntags. Da schreibe ich nur Lachgas-Anzeigen. Das zaehlt als Hobby, oder?", "Ich wollte gerade Feierabend machen. Dann ist mir noch ein Lachgas-Wortspiel eingefallen."}
            }, new String[] {
                "Moin, Anruf verpasst. Ging es um Lachgas? Falls nicht, haette ich trotzdem darueber geredet.",
                "War gerade beschaeftigt. Schreib mir! Ich verspreche fast, nicht sofort Lachgas zu erwaehnen."
            });

    public final String id, name, number, description, avatar;
    public final int color;
    private final String[][] dialogue;
    private final String[] calls;
    PhoneContact(String id, String name, String number, String description, String avatar, int color) {
        this(id, name, number, description, avatar, color, null, null);
    }
    PhoneContact(String id, String name, String number, String description, String avatar, int color,
                 String[][] dialogue, String[] calls) {
        this.id = id; this.name = name; this.number = number; this.description = description;
        this.avatar = avatar; this.color = color; this.dialogue = dialogue; this.calls = calls;
    }
    int presetCount() { return dialogue.length; }
    String preset(int index) { return dialogue[index][0]; }
    String[] replies(int index) {
        String[] row = dialogue[index];
        return java.util.Arrays.copyOfRange(row, 1, row.length);
    }
    String[] callReplies() { return calls; }
}
