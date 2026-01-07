package com.weelo.logistics.utils

import android.text.Html
import android.text.Spanned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Data Sanitizer - Escapes user-generated content for safe display
 * 
 * SECURITY: Prevents XSS (Cross-Site Scripting) attacks
 * OUTPUT SAFETY: Ensures user input is safely displayed
 * 
 * Use cases:
 * - Displaying user names
 * - Showing user comments
 * - Rendering search results
 * - Showing trip descriptions
 * - Displaying addresses
 * 
 * What this prevents:
 * - HTML injection
 * - JavaScript injection
 * - SQL injection (when combined with parameterized queries)
 * - Special character exploits
 */
object DataSanitizer {
    
    /**
     * Escape HTML special characters
     * Prevents XSS attacks when displaying user content
     */
    fun escapeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
    }
    
    /**
     * Strip HTML tags completely
     * Use when you want plain text only
     */
    fun stripHtml(input: String): String {
        return input.replace(Regex("<[^>]*>"), "")
    }
    
    /**
     * Sanitize for display in Compose UI
     * Safe for Text composables
     */
    fun sanitizeForDisplay(input: String?): String {
        if (input.isNullOrBlank()) return ""
        
        return input
            .trim()
            .take(5000) // Prevent memory issues with huge strings
            .let { escapeHtml(it) }
    }
    
    /**
     * Sanitize and truncate with ellipsis
     */
    fun sanitizeAndTruncate(input: String?, maxLength: Int = 100): String {
        val sanitized = sanitizeForDisplay(input)
        return if (sanitized.length > maxLength) {
            sanitized.take(maxLength - 3) + "..."
        } else {
            sanitized
        }
    }
    
    /**
     * Sanitize URL - Only allow http/https
     */
    fun sanitizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            null // Invalid URL
        }
    }
    
    /**
     * Sanitize phone number for display
     * Formats: 9876543210 -> +91-98765-43210
     */
    fun formatPhoneForDisplay(phone: String): String {
        val cleaned = phone.filter { it.isDigit() }
        return when (cleaned.length) {
            10 -> "+91-${cleaned.substring(0, 5)}-${cleaned.substring(5)}"
            else -> phone
        }
    }
    
    /**
     * Sanitize for JSON/API submission
     * Removes control characters and normalizes whitespace
     */
    fun sanitizeForApi(input: String?): String? {
        if (input.isNullOrBlank()) return null
        
        return input
            .trim()
            .replace(Regex("\\p{Cntrl}"), "") // Remove control chars
            .replace(Regex("\\s+"), " ") // Normalize whitespace
    }
    
    /**
     * Create safe AnnotatedString from user input
     * Use when you need styled text
     */
    fun createSafeAnnotatedString(
        input: String,
        highlightPattern: String? = null
    ): AnnotatedString {
        val sanitized = sanitizeForDisplay(input)
        
        return if (highlightPattern != null && highlightPattern.isNotBlank()) {
            buildAnnotatedString {
                val regex = Regex(Regex.escape(highlightPattern), RegexOption.IGNORE_CASE)
                var lastIndex = 0
                
                regex.findAll(sanitized).forEach { match ->
                    // Add text before match
                    append(sanitized.substring(lastIndex, match.range.first))
                    
                    // Add highlighted match
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.value)
                    }
                    
                    lastIndex = match.range.last + 1
                }
                
                // Add remaining text
                if (lastIndex < sanitized.length) {
                    append(sanitized.substring(lastIndex))
                }
            }
        } else {
            AnnotatedString(sanitized)
        }
    }
    
    /**
     * Validate and sanitize file name
     * Prevents directory traversal attacks
     */
    fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace("..", "_")
            .take(255) // Max filename length
    }
    
    /**
     * Convert HTML to plain text (Android View)
     * Use for legacy code that needs Spanned
     */
    @Suppress("DEPRECATION")
    fun fromHtml(html: String): Spanned {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(html)
        }
    }
    
    /**
     * Sanitize array of strings
     */
    fun sanitizeList(items: List<String>?): List<String> {
        return items?.map { sanitizeForDisplay(it) } ?: emptyList()
    }
    
    /**
     * Validate and clean numeric input
     */
    fun sanitizeNumeric(input: String): String {
        return input.filter { it.isDigit() || it == '.' }
    }
    
    /**
     * Sanitize search query
     * Prevents SQL injection in search
     */
    fun sanitizeSearchQuery(query: String): String {
        return query
            .trim()
            .replace(Regex("[;'\"\\\\]"), "") // Remove SQL special chars
            .take(200) // Limit query length
    }
}

/**
 * Extension functions for convenience
 */
fun String?.sanitized(): String = DataSanitizer.sanitizeForDisplay(this)
fun String?.sanitizedTruncated(maxLength: Int = 100): String = 
    DataSanitizer.sanitizeAndTruncate(this, maxLength)

/**
 * USAGE EXAMPLES:
 * 
 * ```kotlin
 * // 1. Display user name
 * Text(text = userName.sanitized())
 * 
 * // 2. Display user comment
 * Text(text = comment.sanitizedTruncated(200))
 * 
 * // 3. Display search results with highlight
 * Text(text = DataSanitizer.createSafeAnnotatedString(result, searchQuery))
 * 
 * // 4. Prepare data for API
 * val cleanData = DataSanitizer.sanitizeForApi(userInput)
 * 
 * // 5. Display phone number
 * Text(text = DataSanitizer.formatPhoneForDisplay(phone))
 * ```
 */

/**
 * SECURITY BEST PRACTICES:
 * 
 * ✅ Always sanitize before display:
 *    - User names
 *    - User comments/descriptions
 *    - Search results
 *    - Addresses
 *    - Any user-generated content
 * 
 * ✅ Backend responsibility:
 *    - Server should ALSO sanitize on receipt
 *    - Use parameterized queries for database
 *    - Validate on server (never trust client)
 *    - Implement Content Security Policy (CSP)
 * 
 * ✅ What to sanitize:
 *    - HTML special chars: < > & " ' /
 *    - Control characters
 *    - SQL special chars in search
 *    - File paths in uploads
 * 
 * ✅ When to sanitize:
 *    - Before displaying user content
 *    - Before storing in database (backend)
 *    - Before using in HTML (if applicable)
 *    - Before logging (combine with SecureLogger)
 */
