/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made 
 * available under the terms of the Eclipse Public License v1.0 
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution 
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html). 
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
*******************************************************************************/
package org.eclipse.flux.jdt.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Utility to calculate the completion replacement string based on JDT Core
 * {@link CompletionProposal}. This class is based on the implementation of JDT
 * UI <code>AbstractJavaCompletionProposal</code> and its subclasses.
 * 
 * @author aboyko
 *
 */
public class CompletionProposalReplacementProvider {
	
	final private static char SPACE = ' ';
	final private static char LPAREN = '(';
	final private static char RPAREN = ')';
	final private static char SEMICOLON = ';';
	final private static char COMMA = ',';
	
	private ICompilationUnit compilationUnit;
	private int offset;
	private String prefix;
	private CompletionProposal proposal;
	private CompletionContext context;
		
	public CompletionProposalReplacementProvider(ICompilationUnit compilationUnit, CompletionProposal proposal, CompletionContext context, int offset, String prefix) {
		super();
		this.compilationUnit = compilationUnit;
		this.offset = offset;
		this.prefix = prefix;
		this.proposal = proposal;
		this.context = context;
	}
	
	
	public StringBuilder createReplacement(List<Integer> positions) {
		return createReplacement(proposal, (char) 0, positions);
	}
	
	public StringBuilder createReplacement(CompletionProposal proposal, char trigger, List<Integer> positions) {
		StringBuilder completionBuffer = new StringBuilder();
		if (isSupportingRequiredProposals(proposal)) {
			CompletionProposal[] requiredProposals= proposal.getRequiredProposals();
			for (int i= 0; requiredProposals != null &&  i < requiredProposals.length; i++) {
				if (requiredProposals[i].getKind() == CompletionProposal.TYPE_REF) {
					appendRequiredType(completionBuffer, requiredProposals[i], trigger, positions);
				} else if (requiredProposals[i].getKind() == CompletionProposal.TYPE_IMPORT) {
					appendImportProposal(completionBuffer, requiredProposals[i], proposal.getKind());
				} else if (requiredProposals[i].getKind() == CompletionProposal.METHOD_IMPORT) {
					appendImportProposal(completionBuffer, requiredProposals[i], proposal.getKind());
				} else if (requiredProposals[i].getKind() == CompletionProposal.FIELD_IMPORT) {
					appendImportProposal(completionBuffer, requiredProposals[i], proposal.getKind());
				} else {
					/*
					 * In 3.3 we only support the above required proposals, see
					 * CompletionProposal#getRequiredProposals()
					 */
					 Assert.isTrue(false);
				}
			}
		}

//			boolean isSmartTrigger= isSmartTrigger(trigger);
			
			appendReplacementString(completionBuffer, proposal, positions);

//			String replacement;
//			if (isSmartTrigger || trigger == (char) 0) {
//				int referenceOffset= offset - prefix.length() + completionBuffer.length();
//				//add ; to the replacement string if replacement string do not end with a semicolon and the document do not already have a ; at the reference offset.
//				if (trigger == ';'
//					&& proposal.getCompletion()[proposal.getCompletion().length - 1] != ';'
//					&& (referenceOffset >= compilationUnit.getBuffer()
//							.getLength() || compilationUnit.getBuffer()
//							.getChar(referenceOffset) != ';')) {
//					completionBuffer.append(';');
//				}
//			} else {
//				StringBuffer buffer= new StringBuffer(getReplacementString());
//
//				// fix for PR #5533. Assumes that no eating takes place.
//				if ((getCursorPosition() > 0 && getCursorPosition() <= buffer.length() && buffer.charAt(getCursorPosition() - 1) != trigger)) {
//					// insert trigger ';' for methods with parameter at the end of the replacement string and not at the cursor position.
//					int length= getReplacementString().length();
//					if (trigger == ';' && getCursorPosition() != length) {
//						if (buffer.charAt(length - 1) != trigger) {
//							buffer.insert(length, trigger);
//						}
//					} else {
//						buffer.insert(getCursorPosition(), trigger);
//						setCursorPosition(getCursorPosition() + 1);
//					}
//				}
//
//				replacement= buffer.toString();
//				setReplacementString(replacement);
//			}
//
//			// PR 47097
//			if (isSmartTrigger) {
//				// avoid inserting redundant semicolon when smart insert is enabled.
//				if (!(trigger == ';' && (completionBuffer.charAt(completionBuffer.length() - 1) == ';' /*|| document.getChar(referenceOffset) == ';'*/))) { //$NON-NLS-1$
//					handleSmartTrigger(trigger, offset - prefix.length() + completionBuffer.length());
//				}
//			}

			return completionBuffer;
	}

	private boolean isSupportingRequiredProposals(CompletionProposal proposal) {
		return proposal != null
				&& (proposal.getKind() == CompletionProposal.METHOD_REF
						|| proposal.getKind() == CompletionProposal.FIELD_REF
						|| proposal.getKind() == CompletionProposal.TYPE_REF
						|| proposal.getKind() == CompletionProposal.CONSTRUCTOR_INVOCATION || proposal
						.getKind() == CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION);
	}
	
	protected boolean hasArgumentList(CompletionProposal proposal) {
		if (CompletionProposal.METHOD_NAME_REFERENCE == proposal.getKind())
			return false;
		char[] completion= proposal.getCompletion();
		return !isInJavadoc() && completion.length > 0 && completion[completion.length - 1] == ')';
	}
	
	private boolean isInJavadoc() {
		return context.isInJavadoc();
	}

	private void appendReplacementString(StringBuilder buffer, CompletionProposal proposal, List<Integer> positions) {
		if (!hasArgumentList(proposal)) {
			buffer.append(String.valueOf(proposal.getCompletion()));
			return;
		}

		// we're inserting a method plus the argument list - respect formatter preferences
		appendMethodNameReplacement(buffer, proposal);

		if (hasParameters(proposal)) {
			appendGuessingCompletion(buffer, proposal, positions);
		}

		buffer.append(RPAREN);
		
		if (canAutomaticallyAppendSemicolon(proposal))
			buffer.append(SEMICOLON);
	}

	private boolean hasParameters(CompletionProposal proposal) throws IllegalArgumentException {
		return Signature.getParameterCount(proposal.getSignature()) > 0;
	}

	private void appendMethodNameReplacement(StringBuilder buffer, CompletionProposal proposal) {
		if (proposal.getKind() == CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER) {
			String coreCompletion= String.valueOf(proposal.getCompletion());
//			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(getTextViewer().getDocument());
//			String replacement= CodeFormatterUtil.format(CodeFormatter.K_EXPRESSION, coreCompletion, 0, lineDelimiter, fInvocationContext.getProject());
//			buffer.append(replacement.substring(0, replacement.lastIndexOf('.') + 1));
			buffer.append(coreCompletion);
		}

		if (proposal.getKind() != CompletionProposal.CONSTRUCTOR_INVOCATION)
			buffer.append(proposal.getName());

		buffer.append(LPAREN);
	}

	private void appendGuessingCompletion(StringBuilder buffer, CompletionProposal proposal, List<Integer> positions) {
		char[][] parameterNames= proposal.findParameterNames(null);

		int count= parameterNames.length;

		for (int i= 0; i < count; i++) {
			if (i != 0) {
				buffer.append(COMMA);
				buffer.append(SPACE);
			}

			char[] argument = parameterNames[i];
			
			positions.add(offset - prefix.length() + buffer.length());
			positions.add(argument.length);

			buffer.append(argument);
		}
	}
	
	private final boolean canAutomaticallyAppendSemicolon(CompletionProposal proposal) {
		return !proposal.isConstructor() && CharOperation.equals(new char[] { Signature.C_VOID }, Signature.getReturnType(proposal.getSignature()));
	}
	
	private StringBuilder appendRequiredType(StringBuilder buffer, CompletionProposal typeProposal, char trigger, List<Integer> positions) {
		
		appendReplacementString(buffer, typeProposal, positions);
		
		if (compilationUnit == null /*|| getContext() != null && getContext().isInJavadoc()*/) {
			return buffer; 
		}

		IJavaProject project= compilationUnit.getJavaProject();
		if (!shouldProposeGenerics(project))
			return buffer;

		char[] completion= typeProposal.getCompletion();
		// don't add parameters for import-completions nor for proposals with an empty completion (e.g. inside the type argument list)
		if (completion.length > 0 && (completion[completion.length - 1] == ';' || completion[completion.length - 1] == '.'))
			return buffer;

		/*
		 * Add parameter types
		 */
		boolean onlyAppendArguments;
		try {
			onlyAppendArguments= proposal.getCompletion().length == 0 && offset > 0 && compilationUnit.getBuffer().getChar(offset - 1) == '<';
		} catch (JavaModelException e) {
			onlyAppendArguments= false;
		}
		if (onlyAppendArguments || shouldAppendArguments(typeProposal, trigger)) {
			appendParameterList(buffer, computeTypeArgumentProposals(typeProposal), positions, onlyAppendArguments);
		}
		return buffer;
	}
	
	private final boolean shouldProposeGenerics(IJavaProject project) {
		String sourceVersion;
		if (project != null)
			sourceVersion= project.getOption(JavaCore.COMPILER_SOURCE, true);
		else
			sourceVersion= JavaCore.getOption(JavaCore.COMPILER_SOURCE);

		return !isVersionLessThan(sourceVersion, JavaCore.VERSION_1_5);
	}
	
	public static boolean isVersionLessThan(String version1, String version2) {
		if (JavaCore.VERSION_CLDC_1_1.equals(version1)) {
			version1= JavaCore.VERSION_1_1 + 'a';
		}
		if (JavaCore.VERSION_CLDC_1_1.equals(version2)) {
			version2= JavaCore.VERSION_1_1 + 'a';
		}
		return version1.compareTo(version2) < 0;
	}
	
	private IJavaElement resolveJavaElement(IJavaProject project, CompletionProposal proposal) throws JavaModelException {
		char[] signature= proposal.getSignature();
		String typeName= SignatureUtil.stripSignatureToFQN(String.valueOf(signature));
		return project.findType(typeName);
	}
	
	private String[] computeTypeArgumentProposals(CompletionProposal proposal) {
		try {
			IType type = (IType) resolveJavaElement(
					compilationUnit.getJavaProject(), proposal);
			if (type == null)
				return new String[0];
	
			ITypeParameter[] parameters = type.getTypeParameters();
			if (parameters.length == 0)
				return new String[0];
	
			String[] arguments = new String[parameters.length];
	
			ITypeBinding expectedTypeBinding = getExpectedTypeForGenericParameters();
			if (expectedTypeBinding != null && expectedTypeBinding.isParameterizedType()) {
				// in this case, the type arguments we propose need to be compatible
				// with the corresponding type parameters to declared type

				IType expectedType= (IType) expectedTypeBinding.getJavaElement();

				IType[] path= TypeProposalUtils.computeInheritancePath(type, expectedType);
				if (path == null)
					// proposed type does not inherit from expected type
					// the user might be looking for an inner type of proposed type
					// to instantiate -> do not add any type arguments
					return new String[0];

				int[] indices= new int[parameters.length];
				for (int paramIdx= 0; paramIdx < parameters.length; paramIdx++) {
					indices[paramIdx]= TypeProposalUtils.mapTypeParameterIndex(path, path.length - 1, paramIdx);
				}

				// for type arguments that are mapped through to the expected type's
				// parameters, take the arguments of the expected type
				ITypeBinding[] typeArguments= expectedTypeBinding.getTypeArguments();
				for (int paramIdx= 0; paramIdx < parameters.length; paramIdx++) {
					if (indices[paramIdx] != -1) {
						// type argument is mapped through
						ITypeBinding binding= typeArguments[indices[paramIdx]];
						arguments[paramIdx]= computeTypeProposal(binding, parameters[paramIdx]);
					}
				}
			}
			
			// for type arguments that are not mapped through to the expected type,
			// take the lower bound of the type parameter
			for (int i = 0; i < arguments.length; i++) {
				if (arguments[i] == null) {
					arguments[i] = computeTypeProposal(parameters[i]);
				}
			}
			return arguments;
		} catch (JavaModelException e) {
			return new String[0];
		}
	}

	private String computeTypeProposal(ITypeParameter parameter) throws JavaModelException {
		String[] bounds= parameter.getBounds();
		String elementName= parameter.getElementName();
		if (bounds.length == 1 && !"java.lang.Object".equals(bounds[0])) //$NON-NLS-1$
			return Signature.getSimpleName(bounds[0]);
		else
			return elementName;
	}

	private String computeTypeProposal(ITypeBinding binding, ITypeParameter parameter) throws JavaModelException {
		final String name = TypeProposalUtils.getTypeQualifiedName(binding);
		if (binding.isWildcardType()) {

			if (binding.isUpperbound()) {
				// replace the wildcard ? with the type parameter name to get "E extends Bound" instead of "? extends Bound"
//				String contextName= name.replaceFirst("\\?", parameter.getElementName()); //$NON-NLS-1$
				// upper bound - the upper bound is the bound itself
				return binding.getBound().getName();
			}

			// no or upper bound - use the type parameter of the inserted type, as it may be more
			// restrictive (eg. List<?> list= new SerializableList<Serializable>())
			return computeTypeProposal(parameter);
		}

		// not a wildcard but a type or type variable - this is unambigously the right thing to insert
		return name;
	}
	
	private StringBuilder appendParameterList(StringBuilder buffer, String[] typeArguments, List<Integer> positions, boolean onlyAppendArguments) {
		if (typeArguments != null && typeArguments.length > 0) {
			final char LESS= '<';
			final char GREATER= '>';
			if (!onlyAppendArguments) {
				buffer.append(LESS);
			}
			StringBuffer separator= new StringBuffer(3);
			separator.append(COMMA);
	
			for (int i= 0; i != typeArguments.length; i++) {
				if (i != 0)
					buffer.append(separator);
	
				positions.add(offset - prefix.length() + buffer.length());
				positions.add(typeArguments[i].length());
				buffer.append(typeArguments[i]);
			}
	
			if (!onlyAppendArguments)
				buffer.append(GREATER);
		}
		return buffer;
	}

	
	private boolean shouldAppendArguments(CompletionProposal proposal,
			char trigger) {
		/*
		 * No argument list if there were any special triggers (for example a
		 * period to qualify an inner type).
		 */
		if (trigger != '\0' && trigger != '<' && trigger != '(')
			return false;

		/*
		 * No argument list if the completion is empty (already within the
		 * argument list).
		 */
		char[] completion = proposal.getCompletion();
		if (completion.length == 0)
			return false;

		/*
		 * No argument list if there already is a generic signature behind the
		 * name.
		 */
		int index = prefix.length() - 1;
		while (index >= 0
				&& Character.isUnicodeIdentifierPart(prefix.charAt(index))
				&& prefix.charAt(index) != '\n')
			--index;

		if (index < 0)
			return true;

		char ch = prefix.charAt(index);
		return ch != '<' && ch != '\n';

	}
	
	private StringBuilder appendImportProposal(StringBuilder buffer, CompletionProposal proposal, int coreKind) {
		int proposalKind= proposal.getKind();
		String qualifiedTypeName= null;
		char[] qualifiedType= null;
 		if (proposalKind == CompletionProposal.TYPE_IMPORT) {
 			qualifiedType= proposal.getSignature();
 	 		qualifiedTypeName= String.valueOf(Signature.toCharArray(qualifiedType));
 		} else if (proposalKind == CompletionProposal.METHOD_IMPORT || proposalKind == CompletionProposal.FIELD_IMPORT) {
            qualifiedType= Signature.getTypeErasure(proposal.getDeclarationSignature());
            qualifiedTypeName= String.valueOf(Signature.toCharArray(qualifiedType));
		} else {
			/*
			 * In 3.3 we only support the above import proposals, see
			 * CompletionProposal#getRequiredProposals()
			 */
			 Assert.isTrue(false);
		}

// 		/* Add imports if the preference is on. */
// 		fImportRewrite= createImportRewrite();
// 		if (fImportRewrite != null) {
//	 		if (proposalKind == CompletionProposal.TYPE_IMPORT) {
//	 			String simpleType= fImportRewrite.addImport(qualifiedTypeName, fImportContext);
//		 		if (fParentProposalKind == CompletionProposal.METHOD_REF)
//		 			return simpleType + "."; //$NON-NLS-1$
// 			} else {
//				String res= fImportRewrite.addStaticImport(qualifiedTypeName, String.valueOf(fProposal.getName()), proposalKind == CompletionProposal.FIELD_IMPORT, fImportContext);
//				int dot= res.lastIndexOf('.');
//				if (dot != -1) {
//					String typeName= fImportRewrite.addImport(res.substring(0, dot), fImportContext);
//					return typeName + '.';
//				}
//			}
//	 		return ""; //$NON-NLS-1$
//	 	}

		// Case where we don't have an import rewrite (see allowAddingImports)

		if (compilationUnit != null && isImplicitImport(Signature.getQualifier(qualifiedTypeName), compilationUnit)) {
			/* No imports for implicit imports. */

			if (proposal.getKind() == CompletionProposal.TYPE_IMPORT && coreKind == CompletionProposal.FIELD_REF)
				return buffer; //$NON-NLS-1$
			qualifiedTypeName= String.valueOf(Signature.getSignatureSimpleName(qualifiedType));
		}
		buffer.append(qualifiedTypeName);
		buffer.append('.');
		return buffer;
	}

	private static boolean isImplicitImport(String qualifier, ICompilationUnit cu) {
		if ("java.lang".equals(qualifier)) {  //$NON-NLS-1$
			return true;
		}
		String packageName= cu.getParent().getElementName();
		if (qualifier.equals(packageName)) {
			return true;
		}
		String typeName= JavaCore.removeJavaLikeExtension(cu.getElementName());
		String mainTypeName= concatenateName(packageName, typeName);
		return qualifier.equals(mainTypeName);
	}
	
	private static String concatenateName(String name1, String name2) {
		StringBuffer buf= new StringBuffer();
		if (name1 != null && name1.length() > 0) {
			buf.append(name1);
		}
		if (name2 != null && name2.length() > 0) {
			if (buf.length() > 0) {
				buf.append('.');
			}
			buf.append(name2);
		}
		return buf.toString();
	}
	
	private ITypeBinding getExpectedTypeForGenericParameters() {
		char[][] chKeys= context.getExpectedTypesKeys();
		if (chKeys == null || chKeys.length == 0)
			return null;

		String[] keys= new String[chKeys.length];
		for (int i= 0; i < keys.length; i++) {
			keys[i]= String.valueOf(chKeys[0]);
		}

		final ASTParser parser= ASTParser.newParser(AST.JLS8);
		parser.setProject(compilationUnit.getJavaProject());
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);

		final Map<String, IBinding> bindings= new HashMap<String, IBinding>();
		ASTRequestor requestor= new ASTRequestor() {
			@Override
			public void acceptBinding(String bindingKey, IBinding binding) {
				bindings.put(bindingKey, binding);
			}
		};
		parser.createASTs(new ICompilationUnit[0], keys, requestor, null);

		if (bindings.size() > 0)
			return (ITypeBinding) bindings.get(keys[0]);

		return null;
	}
	
	//	private boolean isSmartTrigger(char trigger) {
	//		return false;
	//	}
	//	
	//	private void handleSmartTrigger(char trigger, int refrenceOffset) {
	//		
	//	}

}
