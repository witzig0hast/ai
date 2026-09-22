from app.services.sentence_splitter import split_ready_sentence


def test_no_boundary_keeps_accumulating():
    sentence, remainder = split_ready_sentence("Der Himmel ist heute")
    assert sentence is None
    assert remainder == "Der Himmel ist heute"


def test_splits_on_sentence_end():
    sentence, remainder = split_ready_sentence("Es ist sonnig. Und warm")
    assert sentence == "Es ist sonnig. "
    assert remainder == "Und warm"


def test_splits_on_question_mark():
    sentence, remainder = split_ready_sentence("Wie geht es dir? Alles gut")
    assert sentence == "Wie geht es dir? "
    assert remainder == "Alles gut"


def test_short_fragment_not_split():
    # A lone "." right at the start shouldn't trigger a near-empty utterance.
    sentence, remainder = split_ready_sentence(". Rest vom Satz")
    assert sentence is None


def test_force_flush_on_long_unpunctuated_buffer():
    long_buffer = "wort " * 60  # well past the force-flush threshold, no punctuation
    sentence, remainder = split_ready_sentence(long_buffer)
    assert sentence is not None
    assert sentence.endswith(" ")
    assert (sentence + remainder) == long_buffer
