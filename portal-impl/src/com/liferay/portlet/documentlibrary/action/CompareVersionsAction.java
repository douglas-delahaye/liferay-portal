/**
 * Copyright (c) 2000-2008 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portlet.documentlibrary.action;

import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.security.auth.PrincipalException;
import com.liferay.portal.security.permission.ActionKeys;
import com.liferay.portal.struts.PortletAction;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PrefsPropsUtil;
import com.liferay.portal.util.PropsUtil;
import com.liferay.portal.util.PropsValues;
import com.liferay.portal.util.WebKeys;
import com.liferay.portlet.documentlibrary.NoSuchFileEntryException;
import com.liferay.portlet.documentlibrary.service.DLFileEntryLocalServiceUtil;
import com.liferay.portlet.documentlibrary.service.permission.DLFileEntryPermission;
import com.liferay.portlet.documentlibrary.util.DocumentConversionUtil;
import com.liferay.util.FileUtil;
import com.liferay.util.diff.DiffUtil;
import com.liferay.util.servlet.SessionErrors;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.List;

import javax.portlet.PortletConfig;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * <a href="CompareVersionsAction.java.html"><b><i>View Source</i></b></a>
 *
 * @author Bruno Farache
 *
 */
public class CompareVersionsAction extends PortletAction {

	public ActionForward render(
			ActionMapping mapping, ActionForm form, PortletConfig config,
			RenderRequest req, RenderResponse res)
		throws Exception {

		try {
			compareVersions(req);
		}
		catch (Exception e) {
			if (e instanceof NoSuchFileEntryException ||
				e instanceof PrincipalException) {

				SessionErrors.add(req, e.getClass().getName());

				setForward(req, "portlet.document_library.error");
			}
			else {
				throw e;
			}
		}

		return mapping.findForward("portlet.document_library.compare_versions");
	}

	protected void compareVersions(RenderRequest req) throws Exception {
		ThemeDisplay themeDisplay =
			(ThemeDisplay)req.getAttribute(WebKeys.THEME_DISPLAY);

		long companyId = themeDisplay.getCompanyId();
		long userId = themeDisplay.getUserId();

		long fileEntryId = ParamUtil.getLong(req, "fileEntryId");

		long folderId = ParamUtil.getLong(req, "folderId");
		String name = ParamUtil.getString(req, "name");

		DLFileEntryPermission.check(
			themeDisplay.getPermissionChecker(), folderId, name,
			ActionKeys.VIEW);

		String extension = FileUtil.getExtension(name);

		String titleWithExtension = ParamUtil.getString(
			req, "titleWithExtension");

		double sourceVersion = ParamUtil.getDouble(req, "sourceVersion");
		double targetVersion = ParamUtil.getDouble(req, "targetVersion");

		InputStream sourceIs = DLFileEntryLocalServiceUtil.getFileAsStream(
			companyId, userId, folderId, name, sourceVersion);
		InputStream targetIs = DLFileEntryLocalServiceUtil.getFileAsStream(
			companyId, userId, folderId, name, targetVersion);

		if (extension.equals("htm") || extension.equals("html") ||
			extension.equals("xml")) {

			String escapedSource = HtmlUtil.escape(StringUtil.read(sourceIs));
			String escapedTarget = HtmlUtil.escape(StringUtil.read(targetIs));

			sourceIs = new ByteArrayInputStream(
				escapedSource.getBytes(StringPool.UTF8));
			targetIs = new ByteArrayInputStream(
				escapedTarget.getBytes(StringPool.UTF8));
		}

		if (PrefsPropsUtil.getBoolean(
				PropsUtil.OPENOFFICE_SERVER_ENABLED,
				PropsValues.OPENOFFICE_SERVER_ENABLED) &&
			isConvertBeforeCompare(extension)) {

			String sourceTempFileId = DocumentConversionUtil.getTempFileId(
				fileEntryId, sourceVersion);
			String targetTempFileId = DocumentConversionUtil.getTempFileId(
				fileEntryId, targetVersion);

			sourceIs = DocumentConversionUtil.convert(
				sourceTempFileId, sourceIs, extension, "txt");
			targetIs = DocumentConversionUtil.convert(
				targetTempFileId, targetIs, extension, "txt");
		}

		List[] diffResults = DiffUtil.diff(
			new InputStreamReader(sourceIs), new InputStreamReader(targetIs));

		req.setAttribute(
			WebKeys.SOURCE_NAME,
			titleWithExtension + StringPool.SPACE + sourceVersion);
		req.setAttribute(
			WebKeys.TARGET_NAME,
			titleWithExtension + StringPool.SPACE + targetVersion);
		req.setAttribute(WebKeys.DIFF_RESULTS, diffResults);
	}

	protected boolean isConvertBeforeCompare(String extension) {
		if (extension.equals("txt")) {
			return false;
		}

		String[] conversions = DocumentConversionUtil.getConversions(extension);

		for (int i = 0; i < conversions.length; i++) {
			if (conversions[i].equals("txt")) {
				return true;
			}
		}

		return false;
	}

}