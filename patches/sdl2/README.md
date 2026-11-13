# Local SDL2 changes

`ut99-android.patch` records the local changes against SDL2 commit
`5d249570393f7a37e037abf22cd6012a4cc56a71` from
https://github.com/libsdl-org/SDL. These files are altered from upstream SDL2.
The upstream SDL license is retained in `LICENSE.txt`.

For a clean checkout at that exact commit, check and apply the patch with:

```sh
git -C research/SDL2 apply --check ../../patches/sdl2/ut99-android.patch
git -C research/SDL2 apply ../../patches/sdl2/ut99-android.patch
```

The current checkout already contains the changes; do not apply the patch a
second time. A reverse `git apply --check` passed against that checkout. This
captures the SDL source modifications, not the missing Android library build
recipes or the proprietary runtime; it does not make a clean build complete.
