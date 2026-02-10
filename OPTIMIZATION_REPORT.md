# Weelo Captain + Backend Optimization Report

## Scope
- Profile image caching (Instagram-style behavior)
- App smoothness and performance
- Backend standards, scalability, modularity

## Summary of Required Changes
### 1) Profile Image Caching (App + Backend)
**Goal:** Profile photos should not refetch on every app open unless the image changes.

**App Changes**
- Use centralized image loader and cached image component everywhere:
  - Replace direct `AsyncImage` in `DriverProfileScreenNew.kt` with `OptimizedNetworkImage`.
  - Ensure all profile/license images use the same cached component.
- Avoid recreating image requests on every recomposition:
  - Use `remember(imageUrl)` in image requests (already in `OptimizedNetworkImage`).
- Do not refetch profile data on every screen open:
  - Add a cache layer (DataStore or in‑memory) to store `DriverProfileWithPhotos` and `UserProfile`.
  - Introduce a TTL (e.g., 10–30 minutes).
  - Only call `loadProfile()` when cache is stale or after updates.

**Backend Changes**
- Use stable photo URLs:
  - Prefer public S3 URLs or versioned URLs rather than new pre‑signed URLs on every fetch.
  - When photo updates, change URL/version; otherwise reuse.
- Add caching for profile responses:
  - Use `cacheService` for `/profile` and `/driver/profile` (TTL 5–10 min).
  - Add `Cache-Control` headers for profile endpoints.

---

## App Performance & Smoothness (Frontend)
### Issues Found
- `RetrofitClient` cache + retry + offline cache are disabled (commented out).
- `AsyncImage` used directly in multiple places, bypassing centralized cache settings.
- Repeated profile fetches on screen open cause network churn and UI jank.

### Recommended Fixes
- Enable HTTP cache and offline cache in `RetrofitClient`:
  - Uncomment `.cache(cache)`, `.addNetworkInterceptor(cacheInterceptor)`, `.addInterceptor(offlineCacheInterceptor)`.
- Reduce logging in production (`HttpLoggingInterceptor.Level.BODY` is heavy).
- For profile screens:
  - Load cached data first, update in background.
  - Avoid full screen recomposition from repeated network calls.

---

## Backend Standards + Scalability + Modularity
### Issues Found
- Profile module does not use existing caching layer (`cache.service.ts`).
- Profile endpoints lack `Cache-Control` headers.
- Pre‑signed URLs likely refreshed too often, causing app re-downloads.

### Recommended Fixes
- Use `cacheService` for profile GETs (short TTL).
- Add cache headers for profile responses.
- Return stable, versioned image URLs unless photo changes.
- Ensure Redis is enabled in production for shared cache.

---

## Concrete Change List (File-by-File)
### App (`Desktop/Weelo captain`)
1. `app/src/main/java/com/weelo/logistics/ui/driver/DriverProfileScreenNew.kt`
   - Replace `AsyncImage` with `OptimizedNetworkImage`.
   - Ensure `imageUrl` is stable and cached.

2. `app/src/main/java/com/weelo/logistics/ui/components/OptimizedImage.kt`
   - Ensure memory + disk cache enabled (already set).
   - Optional: add placeholder + error support for consistency.

3. `app/src/main/java/com/weelo/logistics/ui/driver/DriverProfileViewModel.kt`
   - Add cache layer for `loadProfile()`.
   - Avoid network fetch if cached profile still valid.

4. `app/src/main/java/com/weelo/logistics/data/remote/RetrofitClient.kt`
   - Enable caching interceptors and disk cache.
   - Reduce logging level for production builds.

### Backend (`Desktop/Weelo-backend`)
1. `src/modules/profile/profile.routes.ts`
   - Add `Cache-Control` headers for GET endpoints.

2. `src/modules/profile/profile.service.ts`
   - Cache profile objects using `cacheService`.

3. `src/shared/services/s3-upload.service.ts`
   - Ensure consistent stable URL strategy (avoid new signed URL every fetch).

---

## Notes on Instagram-Style Behavior
- Instagram keeps the same image URL unless the image changes.
- The app caches the image (memory + disk) and uses HTTP cache headers.
- Only when the user updates the photo is a new URL generated.

---

## Next Steps (If Approved)
- Implement the changes above in both app + backend.
- Test profile photo persistence across app restarts.
- Validate caching works with backend responses.
