#!/bin/sh
# must be run in submodules/hidapi
git restore .
git apply ../diffs/Android.mk.patch
