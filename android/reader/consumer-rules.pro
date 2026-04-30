# nfc-emv-toolkit android/reader: consumer ProGuard rules.
# Empty for v0.2.0 - Reader's public API uses kotlinx.coroutines.flow types
# which kotlinx-coroutines ships its own consumer rules for. Add here only
# if downstream R8 strips a public symbol from this module.
