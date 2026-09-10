from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from opportunity_fabric.models import Resource
from opportunity_fabric.plugins.quantus import QuantusPlugin
from opportunity_fabric.quantus_tools import build_plan, execute_build


class QuantusTermuxNoGoTests(unittest.TestCase):
    def test_plugin_marks_termux_arm64_no_go(self):
        resource = Resource(
            name="android-test",
            kind="device",
            os="android",
            arch="aarch64",
            cpu_count=8,
            memory_mb=12288,
            free_disk_mb=100000,
            gpu=None,
            termux=True,
            provider="local",
        )
        decision = QuantusPlugin().evaluate(resource)
        self.assertFalse(decision.feasible)
        self.assertEqual(decision.score, 0)
        self.assertEqual(decision.mode, "no-go-android-termux")
        self.assertTrue(any("Do not rerun" in action for action in decision.next_actions))

    @patch.dict(
        os.environ,
        {
            "PREFIX": "/data/data/com.termux/files/usr",
            "TERMUX_VERSION": "test",
        },
        clear=False,
    )
    @patch("opportunity_fabric.quantus_tools.platform.machine", return_value="aarch64")
    def test_build_plan_is_empty_on_termux_arm64(self, _machine):
        plan = build_plan(2)
        self.assertTrue(plan["build_disabled"])
        self.assertEqual(plan["decision"], "NO_GO")
        self.assertEqual(plan["commands"], [])

    @patch.dict(
        os.environ,
        {
            "PREFIX": "/data/data/com.termux/files/usr",
            "TERMUX_VERSION": "test",
        },
        clear=False,
    )
    @patch("opportunity_fabric.quantus_tools.platform.machine", return_value="aarch64")
    def test_execute_build_stops_before_subprocess(self, _machine):
        with patch("opportunity_fabric.quantus_tools.subprocess.run") as run:
            result = execute_build(2)
        run.assert_not_called()
        self.assertFalse(result["ok"])
        self.assertFalse(result["executed"])
        self.assertEqual(result["stage"], "policy-gate")
        self.assertEqual(result["decision"], "NO_GO")


if __name__ == "__main__":
    unittest.main()
