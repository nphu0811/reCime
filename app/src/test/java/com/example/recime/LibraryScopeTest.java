package com.example.recime;
import com.example.recime.helper.LibraryScope;
import com.example.recime.model.Recipe;
import org.junit.Test;
import static org.junit.Assert.*;
public class LibraryScopeTest {
  @Test public void catalogAndPersonalAreDisjointWithoutChangingRows(){
    Recipe personal=new Recipe();personal.title="Personal";
    Recipe catalog=new Recipe();catalog.catalogKey="open:1";catalog.source="https://en.wikibooks.org/";catalog.title="Catalog";
    assertTrue(LibraryScope.includes(personal,false));assertFalse(LibraryScope.includes(personal,true));
    assertFalse(LibraryScope.includes(catalog,false));assertTrue(LibraryScope.includes(catalog,true));
    assertEquals("open:1",catalog.catalogKey);assertEquals("Personal",personal.title);
  }
}
