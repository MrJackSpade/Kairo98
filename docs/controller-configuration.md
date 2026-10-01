# Controller configuration

Controller setup, Auto detection, default variants, and migration are implemented
in the pinned shared frontend. See [shared controller configuration](../shared/docs/controller-configuration.md).

Authored catalog controller records use `defaults.withoutSticks` and optional
`defaults.withSticks`. Legacy `bindings` are retained for old clients and migrated
unchanged into Without Sticks defaults. Missing With Sticks variants fall back;
explicit empty arrays disable bindings. User overrides retain precedence.

The global Without Sticks default uses the existing D-pad and button assignments.
With Sticks also includes the existing left-stick mouse and right-stick key mappings.
Touchscreen controls and arrangements are unchanged.
