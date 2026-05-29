"""
Logging utilities for sanitization to prevent log injection (S5145).
"""

def sanitize_log(input_val) -> str:
    """
    Sanitize input values to prevent log injection (pythonsecurity:S5145).
    Replaces CR and LF characters to ensure single-line logs.
    """
    if input_val is None:
        return ""
    val_str = str(input_val)
    return val_str.replace('\r', '').replace('\n', '')
