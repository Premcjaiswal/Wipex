/**
 * Independent backend safety checks for sanitization requests - system
 * disk, live mode, device existence, serial confirmation, single active
 * job. The frontend enforces the same rules for UX, but this package is
 * what actually decides.
 */
package com.zerowipe.safety;
