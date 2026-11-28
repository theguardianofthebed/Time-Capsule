"""Check the actual Views resources used for recovery and credential entry."""
import unittest
from pathlib import Path
from xml.etree import ElementTree

RES = Path(__file__).resolve().parents[1] / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class NativeControlsTest(unittest.TestCase):
    def test_header_actions_have_48dp_targets_and_action_labels(self):
        expected = {"btnHeaderAccount": "Account", "btnHeaderRefresh": "Refresh capsules",
                    "btnBack": "Go back", "btnS07Back": "Go back"}
        strings = {e.get("name"): e.text for e in ElementTree.parse(RES / "values/strings.xml").getroot()}
        seen = set()
        for path in (RES / "layout").glob("fragment_s*.xml"):
            for e in ElementTree.parse(path).iter("ImageButton"):
                key = e.get(ANDROID + "id", "").split("/")[-1]
                if key in expected:
                    with self.subTest(screen=path.stem, control=key):
                        seen.add(key)
                        for axis in ("layout_width", "layout_height"):
                            self.assertGreaterEqual(int(e.get(ANDROID + axis).removesuffix("dp")), 48)
                        label = e.get(ANDROID + "contentDescription")
                        self.assertEqual(strings.get(label.removeprefix("@string/"), label), expected[key])
        self.assertEqual(seen, set(expected))

    def test_detail_photo_has_a_spoken_label(self):
        layout = ElementTree.parse(RES / "layout/fragment_s07_capsule_detail.xml")
        photo = next(e for e in layout.iter("ImageView") if e.get(ANDROID + "id") == "@+id/ivS07Photo")
        strings = {e.get("name"): e.text for e in ElementTree.parse(RES / "values/strings.xml").getroot()}
        label = photo.get(ANDROID + "contentDescription", "").removeprefix("@string/")
        self.assertEqual(strings.get(label), "Attached capsule photo")

    def test_password_fields_opt_out_of_saved_view_state(self):
        for path in (RES / "layout").glob("fragment_s0[12]*.xml"):
            for field in ElementTree.parse(path).iter("EditText"):
                if "password" in field.get(ANDROID + "inputType", "").lower():
                    with self.subTest(screen=path.stem, field=field.get(ANDROID + "id")):
                        self.assertEqual(field.get(ANDROID + "saveEnabled"), "false")

    def test_edit_fields_have_persistent_labels(self):
        for path in (RES / "layout").glob("fragment_s*.xml"):
            layout = ElementTree.parse(path)
            labels = {e.get(ANDROID + "labelFor") for e in layout.iter("TextView") if e.get(ANDROID + "text")}
            for field in layout.iter("EditText"):
                with self.subTest(screen=path.stem, field=field.get(ANDROID + "id")):
                    self.assertIn(field.get(ANDROID + "id").replace("@+id/", "@id/"), labels)

    def test_navigation_items_have_minimum_height(self):
        for path in (RES / "layout").glob("fragment_s*.xml"):
            for e in ElementTree.parse(path).iter("LinearLayout"):
                if "Nav" in e.get(ANDROID + "id", "") and "btn" in e.get(ANDROID + "id", ""):
                    with self.subTest(screen=path.stem, control=e.get(ANDROID + "id")):
                        self.assertGreaterEqual(int(e.get(ANDROID + "minHeight", "0dp").removesuffix("dp")), 48)

    def test_form_fields_and_submit_buttons_grow_with_text(self):
        for path in (RES / "layout").glob("fragment_s0[128]*.xml"):
            layout = ElementTree.parse(path)
            parents = {child: parent for parent in layout.iter() for child in parent}
            for view in layout.iter():
                if view.tag in {"Button", "EditText"}:
                    with self.subTest(screen=path.stem, field=view.get(ANDROID + "id")):
                        self.assertEqual(view.get(ANDROID + "layout_height"), "wrap_content")
                        if view.tag == "EditText":
                            self.assertEqual(parents[view].get(ANDROID + "layout_height"), "wrap_content")

    def test_input_hints_are_legible_on_form_surface(self):
        colors = {e.get("name"): e.text for e in ElementTree.parse(RES / "values/colors.xml").getroot()}

        def luminance(value):
            channels = [int(value[i:i+2], 16) / 255 for i in (1, 3, 5)]
            linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in channels]
            return sum(v * k for v, k in zip(linear, (0.2126, 0.7152, 0.0722)))

        for path in (RES / "layout").glob("fragment_s*.xml"):
            for view in ElementTree.parse(path).iter("EditText"):
                value = view.get(ANDROID + "textColorHint")
                if value:
                    with self.subTest(screen=path.stem, field=view.get(ANDROID + "id")):
                        color = colors.get(value.removeprefix("@color/"), value)
                        ratio = (luminance(colors["capsule_bg"]) + 0.05) / (luminance(color) + 0.05)
                        self.assertGreaterEqual(ratio, 4.5)
