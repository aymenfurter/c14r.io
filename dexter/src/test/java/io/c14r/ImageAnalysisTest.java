package io.c14r;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageAnalysisTest extends ImageAnalysis {
    @Test
    void testStripLast() throws Exception {
        assertEquals("test", stripLast("test"));
        assertEquals("test\n\ntest\n", stripLast("test\n\ntest\n\ntest"));
    }
}
