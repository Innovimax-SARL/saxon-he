////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Base64BinaryValue;

import java.io.*;
import java.net.URLConnection;


public class BinaryResource implements Resource {

    private String href = null;
    private String contentType = null;
    private byte[] data;
    private URLConnection connection = null;
    private InputStream inputStream = null;

    /**
     * ResourceFactory suitable for creating a BinaryResource
     */

    public static final ResourceFactory FACTORY = new ResourceFactory() {
        public Resource makeResource(Configuration config, String resourceURI, String contentType, AbstractResourceCollection.InputDetails details) throws XPathException {
            return new BinaryResource(resourceURI, contentType, details.inputStream);
        }
    };

    /**
     * Create a binary resource
     *
     * @param href        the URI of the resource
     * @param contentType the media type of the resource
     * @param in          inputStream containing the binary content of the resource. Note that the InputStream
     *                    is not consumed by this method, but is retained for use by the getItem() method.
     */

    public BinaryResource(String href, String contentType, InputStream in) {
        this.contentType = contentType;
        this.href = href;
        this.inputStream = in;
    }

    /**
     * Set the content of the resource as an array of bytes
     *
     * @param data the content of the resource
     */

    public void setData(byte[] data) {
        this.data = data;
    }

    /**
     * Get the URI of the resource
     *
     * @return the URI of the resource
     */

    public String getResourceURI() {
        return href;
    }

    private byte[] readBinaryFromConn(URLConnection con) throws XPathException {
        InputStream raw = null;
        this.connection = con;
        try {
            raw = connection.getInputStream();

            int contentLength = connection.getContentLength();
            InputStream in = new BufferedInputStream(raw);
            byte[] data = new byte[contentLength];
            int bytesRead = 0;
            int offset = 0;
            while (offset < contentLength) {
                bytesRead = in.read(data, offset, data.length - offset);
                if (bytesRead == -1) {
                    break;
                }
                offset += bytesRead;
            }
            in.close();

            if (offset != contentLength) {
                throw new XPathException("Only read " + offset + " bytes; Expected " + contentLength + " bytes");
            }
            return data;
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    /**
     * Utility method to construct an array of bytes from the content of an InputStream
     * @param in the input stream. The method consumes the input stream but does not close it.
     * @param path file name or URI used only for diagnostics
     * @return byte array representing the content of the InputStream
     * @throws XPathException
     */

    public static byte[] readBinaryFromStream(InputStream in, String path) throws XPathException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        try {
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new XPathException("Failed to read: " + path + " " + e);
        }
    }

    public Item getItem(XPathContext context) throws XPathException {
        if (data != null) {
            return new Base64BinaryValue(data);
        } else if (connection != null) {
            data = readBinaryFromConn(connection);
            return new Base64BinaryValue(data);
        } else if (inputStream != null) {
            data = readBinaryFromStream(inputStream, href);
            return new Base64BinaryValue(data);
        }
        return null;

    }

    public String getContentType() {
        return contentType;
    }


}
