package nl.adaptivity.android.darwin;

import android.support.annotation.NonNull;
import android.text.TextUtils.StringSplitter;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by pdvrieze on 03/05/16.
 */
public class MyCookieStore implements CookieStore {
  private final ReentrantLock mLock;

  private final Map<String,List<HttpCookie>> cookieMap;
  private final Map<URI,List<HttpCookie>> uriMap;

  public MyCookieStore() {
    mLock = new ReentrantLock();
    cookieMap = new HashMap<>();
    uriMap = new HashMap<>();
  }

  @Override
  public void add(final URI uri, final HttpCookie cookie) {
    if (cookie==null) throw new NullPointerException();
    if (cookie.getMaxAge()==0) { return; }
    mLock.lock();
    try {
      String host = uri.getHost();
      List<HttpCookie> list = cookieMap.get(host);
      if (list == null) {
        list = new ArrayList<>();
        cookieMap.put(host, list);
      }
      removeSameCookieFromList(list, cookie);
      list.add(cookie);

      try {
        URI cookieUri = getCookieUri(uri);
        list = uriMap.get(cookieUri);
        if (list == null) { list = new ArrayList<>(); uriMap.put(cookieUri, list); }
        removeSameCookieFromList(list, cookie);
        list.add(cookie);
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }


    } finally {
      mLock.unlock();
    }
  }

  private boolean removeSameCookieFromList(final List<HttpCookie> list, final HttpCookie cookie) {
    if (list==null) { return false; }
    boolean removed = false;
    for(Iterator<HttpCookie> iterator = list.iterator(); iterator.hasNext();) {
      HttpCookie elem = iterator.next();
      if (cookie.getName().equals(elem.getName()) &&
          isEqual(elem.getDomain(), cookie.getDomain()) &&
          isEqualOrNull(elem.getPortlist(), cookie.getPortlist()) &&
          isEqual(elem.getPath(), cookie.getPath())) {
        iterator.remove();
        removed=true;
      }
    }
    return removed;
  }

  private static boolean isEqual(final Object val1, final Object val2) {
    return val1==null ? val2==null : val1.equals(val2);
  }

  private static boolean isEqualOrNull(final Object val1, final Object val2) {
    return val1==null || val2==null || val1.equals(val2);
  }

  @NonNull
  private URI getCookieUri(final URI uri) throws URISyntaxException {return new URI("http", uri.getHost(), uri.getPath());}

  @Override
  public List<HttpCookie> get(final URI uri) {
    mLock.lock();
    try {
      String host = uri.getHost();
      final List<HttpCookie> rawList = cookieMap.get(host);
      if (rawList==null|| rawList.isEmpty()) { return Collections.emptyList(); }
      boolean uriIsSecure="https".equals(uri.getScheme());

      List<HttpCookie> result = new ArrayList<>();
      for(Iterator<HttpCookie> it = rawList.iterator(); it.hasNext();) {
        HttpCookie candidate = it.next();
        if (candidate.hasExpired()) { it.remove(); }
        if (!candidate.getSecure()|| uriIsSecure) {
          result.add(candidate);
        }
      }
      return result;
    } finally {
      mLock.unlock();
    }
  }

  @Override
  public List<HttpCookie> getCookies() {
    ArrayList<HttpCookie> result = new ArrayList<>();
    mLock.lock();
    try {
      for (List<HttpCookie> list : cookieMap.values()) {
        for (Iterator<HttpCookie> it = list.iterator(); it.hasNext(); ) {
          HttpCookie cookie = it.next();
          if (cookie.hasExpired()) {
            it.remove();
          } else {
            result.add(cookie);
          }
        }
      }
    } finally {
      mLock.unlock();
    }
    return result;
  }

  @Override
  public List<URI> getURIs() {
    mLock.lock();
    try {
      return new ArrayList<>(uriMap.keySet());
    } finally {
      mLock.unlock();
    }
  }

  @Override
  public boolean remove(final URI uri, final HttpCookie cookie) {
    mLock.lock();
    try {
      boolean result = false;
      List<HttpCookie> list = cookieMap.get(uri.getHost());
      result = removeSameCookieFromList(list, cookie);
      list = uriMap.get(uri);
      result = removeSameCookieFromList(list, cookie) || result;
      return result;
    } finally {
      mLock.unlock();
    }
  }

  @Override
  public boolean removeAll() {

    mLock.lock();
    boolean result = false;
    for(List<HttpCookie> cookieList: cookieMap.values()) {
      if (cookieList.size()>0) {
        result = true;
        break;
      }
    }
    try {
      cookieMap.clear();
      uriMap.clear();
    } finally {
      mLock.unlock();
    }
    return result;
  }
}