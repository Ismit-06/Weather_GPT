from app.services.speech_sanitizer import prepare_text_for_speech

def test_speech_sanitizer_suite():
    # TEST 1
    assert "Rain is likely" in prepare_text_for_speech("Rain is likely ☔")
    assert "☔" not in prepare_text_for_speech("Rain is likely ☔")

    # TEST 2
    res2 = prepare_text_for_speech("4:30 PM — rain expected 🌧️")
    assert "4:30 PM, rain expected" == res2 or "4:30 PM , rain expected" in res2

    # TEST 3
    assert prepare_text_for_speech("🌧️ Heavy rain at 5 PM") == "Heavy rain at 5 PM"

    # TEST 4
    assert prepare_text_for_speech("## Forecast 🌧️") == "Forecast"

    # TEST 5
    res5 = prepare_text_for_speech("- Rain at 4 PM\n\nHeavy rain at 5 PM")
    assert "Rain at 4 PM" in res5 and "Heavy rain at 5 PM" in res5
    assert "-" not in res5

    # TEST 6
    res6 = prepare_text_for_speech("Temperature: 32°C 🌡️")
    assert "32 degrees Celsius" in res6
    assert "🌡️" not in res6

    # TEST 7
    res7 = prepare_text_for_speech("Rain probability: 78% 🌧️")
    assert "78 percent" in res7
    assert "🌧️" not in res7

    # TEST 8
    res8 = prepare_text_for_speech("4:30 PM — Heavy Rain 🌧️☔")
    assert "4:30 PM, Heavy Rain" == res8 or "4:30 PM , Heavy Rain" in res8
    assert "🌧️" not in res8 and "☔" not in res8

    # TEST 9
    res9 = prepare_text_for_speech("### Weather Update\nRain is likely\n☔")
    assert "Weather Update" in res9 and "Rain is likely" in res9
    assert "#" not in res9 and "☔" not in res9

    print("All 9 Master Prompt Speech Sanitizer tests passed successfully!")

if __name__ == "__main__":
    test_speech_sanitizer_suite()
