package com.example.data

data class SongNote(
    val pitch: String,        // e.g. "C4", "E4", "G4"
    val lyric: String,        // Syllable or word
    val frequency: Float      // Expected frequency in Hz
)

data class Song(
    val id: String,
    val title: String,
    val artist: String = "Traditional",
    val difficulty: String,   // Easy, Medium, Hard
    val description: String,
    val notes: List<SongNote>
)

object SongLibrary {
    val songs = listOf(
        Song(
            id = "twinkle",
            title = "Twinkle Twinkle Little Star",
            artist = "Jane Taylor",
            difficulty = "Easy",
            description = "A standard children\\'s classic, perfect for practicing basic finger movements on C, G, A, F, E, and D.",
            notes = listOf(
                SongNote("C4", "Twin-", 261.63f),
                SongNote("C4", "kle", 261.63f),
                SongNote("G4", "twin-", 392.00f),
                SongNote("G4", "kle", 392.00f),
                SongNote("A4", "lit-", 440.00f),
                SongNote("A4", "tle", 440.00f),
                SongNote("G4", "star,", 392.00f),
                
                SongNote("F4", "how", 349.23f),
                SongNote("F4", "I", 349.23f),
                SongNote("E4", "won-", 329.63f),
                SongNote("E4", "der", 329.63f),
                SongNote("D4", "what", 293.66f),
                SongNote("D4", "you", 293.66f),
                SongNote("C4", "are.", 261.63f),
                
                SongNote("G4", "Up", 392.00f),
                SongNote("G4", "a-", 392.00f),
                SongNote("F4", "bove", 349.23f),
                SongNote("F4", "the", 349.23f),
                SongNote("E4", "world", 329.63f),
                SongNote("E4", "so", 329.63f),
                SongNote("D4", "high,", 293.66f),
                
                SongNote("G4", "like", 392.00f),
                SongNote("G4", "a", 392.00f),
                SongNote("F4", "dia-", 349.23f),
                SongNote("F4", "mond", 349.23f),
                SongNote("E4", "in", 329.63f),
                SongNote("E4", "the", 329.63f),
                SongNote("D4", "sky.", 293.66f),
                
                SongNote("C4", "Twin-", 261.63f),
                SongNote("C4", "kle", 261.63f),
                SongNote("G4", "twin-", 392.00f),
                SongNote("G4", "kle", 392.00f),
                SongNote("A4", "lit-", 440.00f),
                SongNote("A4", "tle", 440.00f),
                SongNote("G4", "star,", 392.00f),
                
                SongNote("F4", "how", 349.23f),
                SongNote("F4", "I", 349.23f),
                SongNote("E4", "won-", 329.63f),
                SongNote("E4", "der", 329.63f),
                SongNote("D4", "what", 293.66f),
                SongNote("D4", "you", 293.66f),
                SongNote("C4", "are!", 261.63f)
            )
        ),
        Song(
            id = "birthday",
            title = "Happy Birthday",
            artist = "Patty & Mildred J. Hill",
            difficulty = "Medium",
            description = "The classic celebration track containing slightly wider octave transitions including C4, D4, E4, F4, G4, A4, and C5.",
            notes = listOf(
                SongNote("C4", "Hap-", 261.63f),
                SongNote("C4", "py", 261.63f),
                SongNote("D4", "birth-", 293.66f),
                SongNote("C4", "day", 261.63f),
                SongNote("F4", "to", 349.23f),
                SongNote("E4", "you,", 329.63f),
                
                SongNote("C4", "Hap-", 261.63f),
                SongNote("C4", "py", 261.63f),
                SongNote("D4", "birth-", 293.66f),
                SongNote("C4", "day", 261.63f),
                SongNote("G4", "to", 392.00f),
                SongNote("F4", "you,", 349.23f),
                
                SongNote("C4", "Hap-", 261.63f),
                SongNote("C4", "py", 261.63f),
                SongNote("C5", "birth-", 523.25f),
                SongNote("A4", "day", 440.00f),
                SongNote("F4", "dear", 349.23f),
                SongNote("E4", "mu-", 329.63f),
                SongNote("D4", "sian,", 293.66f),
                
                SongNote("A#4", "Hap-", 466.16f),
                SongNote("A#4", "py", 466.16f),
                SongNote("A4", "birth-", 440.00f),
                SongNote("F4", "day", 349.23f),
                SongNote("G4", "to", 392.00f),
                SongNote("F4", "you!", 349.23f)
            )
        ),
        Song(
            id = "ode",
            title = "Ode to Joy",
            artist = "Ludwig van Beethoven",
            difficulty = "Easy",
            description = "A powerful anthem of joy and harmony. Simple and elegant single-note progression in a compact register.",
            notes = listOf(
                SongNote("E4", "Joy-", 329.63f),
                SongNote("E4", "ful,", 329.63f),
                SongNote("F4", "joy-", 349.23f),
                SongNote("G4", "ful,", 392.00f),
                SongNote("G4", "we", 392.00f),
                SongNote("F4", "a-", 349.23f),
                SongNote("E4", "dore", 329.63f),
                SongNote("D4", "Thee,", 293.66f),
                
                SongNote("C4", "God", 261.63f),
                SongNote("C4", "of", 261.63f),
                SongNote("D4", "glo-", 293.66f),
                SongNote("E4", "ry,", 329.63f),
                SongNote("E4", "Lord", 329.63f),
                SongNote("D4", "of", 293.66f),
                SongNote("D4", "love;", 293.66f),
                
                SongNote("E4", "Hearts", 329.63f),
                SongNote("E4", "un-", 329.63f),
                SongNote("F4", "fold", 349.23f),
                SongNote("G4", "like", 392.00f),
                SongNote("G4", "flow\\'rs", 392.00f),
                SongNote("F4", "be-", 349.23f),
                SongNote("E4", "fore", 329.63f),
                SongNote("D4", "Thee,", 293.66f),
                
                SongNote("C4", "hail-", 261.63f),
                SongNote("C4", "ing", 261.63f),
                SongNote("D4", "Thee", 293.66f),
                SongNote("E4", "as", 329.63f),
                SongNote("D4", "sun", 293.66f),
                SongNote("C4", "a-", 261.63f),
                SongNote("C4", "bove.", 261.63f)
            )
        ),
        Song(
            id = "elise",
            title = "Für Elise",
            artist = "Ludwig van Beethoven",
            difficulty = "Hard",
            description = "Beethoven's famous bagatelle, featuring advanced semitone alterations (E5, D#5) and a fast alternating flow.",
            notes = listOf(
                SongNote("E5", "E-", 659.25f),
                SongNote("D#5", "lise,", 622.25f),
                SongNote("E5", "won-", 659.25f),
                SongNote("D#5", "der-", 622.25f),
                SongNote("E5", "ful", 659.25f),
                SongNote("B4", "and", 493.88f),
                SongNote("D5", "sweet", 587.33f),
                SongNote("C5", "mel-", 523.25f),
                SongNote("A4", "o-", 440.00f),
                SongNote("C4", "dy,", 261.63f),
                SongNote("E4", "grand", 329.63f),
                SongNote("A4", "and", 440.00f),
                SongNote("B4", "pure.", 493.88f)
            )
        )
    )
}
